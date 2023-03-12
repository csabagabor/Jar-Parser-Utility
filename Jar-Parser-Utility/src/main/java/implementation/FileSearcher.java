package implementation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class FileSearcher {

    public static List<Path> allFilesRecursively(String rootPath, BiPredicate<Path, BasicFileAttributes> filter) throws IOException {
        return Files.find(Paths.get(rootPath), Integer.MAX_VALUE, filter).collect(Collectors.toList());
    }

    public static List<Path> allFilesRecursively(String rootPath, BiPredicate<Path, BasicFileAttributes> filter, long limit) throws IOException {
        return Files.find(Paths.get(rootPath), Integer.MAX_VALUE, filter).limit(limit).collect(Collectors.toList());
    }
}
