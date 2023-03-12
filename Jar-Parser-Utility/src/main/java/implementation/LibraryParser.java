package implementation;


import org.objectweb.asm.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class LibraryParser {
    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private static final int MB = 1024 * 1024;

    public static void parse(String rootPath, String outputPath) throws IOException {
        long start = System.currentTimeMillis();

        Map<MavenCoordinates, Set<Path>> allVersionsForGivenArtifact = getAllJarsForGivenArtifact(rootPath);
        System.out.println("Found " + allVersionsForGivenArtifact.size() + " artifacts...");

        allVersionsForGivenArtifact.entrySet().stream().forEach(entry -> {
            MavenCoordinates mavenCoordinates = entry.getKey();
            Set<Path> allVersionJars = entry.getValue();
            //run individual artifacts on a single thread
            executor.submit(() -> {
                Map<String, Map<Version, Map<String, MethodInfo>>> methodsForArtifact = new LinkedHashMap<>();//<className, <version, <methodName, methodInfo>>>
                Set<Version> allVersions = new TreeSet<>();

                populateMethods(allVersionJars, methodsForArtifact, allVersions);
                writeResult(outputPath, mavenCoordinates, methodsForArtifact, allVersions);
            });

        });


        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (
                InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Parsing done");
        System.out.println("Full memory usage " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB);
        System.out.println("Duration:" + (System.currentTimeMillis() - start));
    }

    private static void populateMethods(Set<Path> allVersionJars, Map<String, Map<Version, Map<String, MethodInfo>>> methodsForArtifact, Set<Version> allVersions) {
        allVersionJars.forEach(jarFile -> {
            try {
                Path versionPath = jarFile.getParent();
                String version = versionPath.getFileName().toString();
                allVersions.add(new Version(version));

                try {
                    analyzeJarFile(version, jarFile, methodsForArtifact);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void writeResult(String outputPath, MavenCoordinates mavenCoordinates, Map<String, Map<Version, Map<String, MethodInfo>>> methodsForArtifact, Set<Version> allVersions) {
        try {
            FileWriter fileWriter = new FileWriter(outputPath + File.separator + mavenCoordinates + ".txt");
            PrintWriter output = new PrintWriter(fileWriter);

            methodsForArtifact.forEach((className, methodsAllVersions) -> {
                boolean isFirstVersionFound = false;

                Map<String, MethodInfo> previousVersionMethods = new HashMap<>();
                for (Version version : allVersions) {
                    Map<String, MethodInfo> currentVersionMethods = methodsAllVersions.get(version);
                    if (currentVersionMethods != null) {
                        StringBuilder outputForVersion = new StringBuilder();

                        boolean isFirstVersion = false;
                        if (!isFirstVersionFound) {
                            isFirstVersionFound = true;
                            output.println("\n+###" + className);
                            output.println("@" + version);
                            isFirstVersion = true;
                        }

                        Map<String, MethodInfo> finalPreviousVersionMethods = previousVersionMethods;
                        currentVersionMethods.forEach((methodName, methodInfo) -> {
                            MethodInfo previousMethodInfo = finalPreviousVersionMethods.get(methodName);

                            if (previousMethodInfo == null) {
                                outputForVersion.append("+#" + methodName + methodInfo).append("\n");
                            } else {
                                String differenceFromPreviousMethod = methodInfo.differenceBetween(previousMethodInfo);
                                if (!differenceFromPreviousMethod.isEmpty()) {
                                    outputForVersion.append("*#" + methodName + methodInfo).append("\n");
                                }
                            }
                        });

                        previousVersionMethods.forEach((methodName, methodInfo) -> {
                            MethodInfo currentMethodInfo = currentVersionMethods.get(methodName);

                            if (currentMethodInfo == null) {
                                outputForVersion.append("-#" + methodName + methodInfo).append("\n");
                            }
                        });


                        if (!outputForVersion.toString().isEmpty()) {
                            if (!isFirstVersion) {
                                output.println("@" + version);
                            }
                            output.print(outputForVersion);
                        }

                        previousVersionMethods = currentVersionMethods;
                    } else if (isFirstVersionFound) {
                        output.println("@" + version);
                        output.println("-###" + className);
                        break;
                    }
                }

            });

            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static void analyzeJarFile(String version, Path jarFile, Map<String, Map<Version, Map<String, MethodInfo>>> artifactMethods) throws IOException {
        JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(jarFile));
        JarEntry jarEntry = jarInputStream.getNextJarEntry();
        while (jarEntry != null) {
            if (jarEntry.getName().endsWith(".class")) {
                ClassReader classReader = new ClassReader(jarInputStream);
                classReader.accept(getClassVisitor(version, artifactMethods), 0);
            }
            jarEntry = jarInputStream.getNextJarEntry();
        }
        jarInputStream.close();
    }

    private static ClassVisitor getClassVisitor(String version, Map<String, Map<Version, Map<String, MethodInfo>>> artifactMethods) {
        return new ClassVisitor(Opcodes.ASM9) {
            private final Map<String, MethodInfo> classMethods = new LinkedHashMap<>();
            private boolean isPublic;
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                isPublic = (access & Opcodes.ACC_PUBLIC) != 0;
                className = name;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        boolean isDeprecated = false;

                        @Override
                        public void visitEnd() {
                            classMethods.put(name + descriptor, new MethodInfo(isDeprecated));
                        }

                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (descriptor.equals("Ljava/lang/Deprecated;")) {
                                isDeprecated = true;
                            }
                            return super.visitAnnotation(descriptor, visible);
                        }
                    };
                } else {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

            }

            @Override
            public void visitEnd() {
                if (isPublic) {
                    artifactMethods.computeIfAbsent(className, key -> new TreeMap<>()).put(new Version(version), classMethods);
                }
            }
        };
    }

    private static Map<MavenCoordinates, Set<Path>> getAllJarsForGivenArtifact(String rootPath) throws IOException {
        Map<MavenCoordinates, Set<Path>> allVersionsForGivenArtifact = new HashMap<>();

        List<Path> allJarFiles = FileSearcher.allFilesRecursively(rootPath, (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toString().toLowerCase().endsWith(".jar"));

        allJarFiles.forEach(allJarFile -> {
            try {
                System.out.println("Parsing " + allJarFile.toString());
                Path versionPath = allJarFile.getParent();
                Path artifactPath = versionPath.getParent();
                String groupId = artifactPath.getParent().toString().replace(rootPath, "").replace(File.separator, ".");

                MavenCoordinates mavenCoordinates = new MavenCoordinates(groupId, artifactPath.getFileName().toString());
                allVersionsForGivenArtifact.computeIfAbsent(mavenCoordinates, key -> new HashSet<>()).add(allJarFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return allVersionsForGivenArtifact;
    }
}