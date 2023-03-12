package implementation;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Objects;

public class Version implements Comparable<Version> {
    String version;

    public Version(String version) {
        this.version = version;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Version version1 = (Version) o;
        return Objects.equals(version, version1.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public int compareTo(Version other) {
        DefaultArtifactVersion firstVersion = new DefaultArtifactVersion(version);
        DefaultArtifactVersion secondVersion = new DefaultArtifactVersion(other.version);
        return firstVersion.compareTo(secondVersion);
    }

    @Override
    public String toString() {
        return version;
    }
}