package implementation;

import java.util.Objects;

public class MavenCoordinates {
    String groupId;
    String artifactId;

    public MavenCoordinates(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenCoordinates that = (MavenCoordinates) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }

    @Override
    public String toString() {
        return groupId + '&' + artifactId;
    }
}