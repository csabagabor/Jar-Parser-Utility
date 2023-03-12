package implementation;

public class MethodInfo {
    boolean isDeprecated;

    public MethodInfo(boolean isDeprecated) {
        this.isDeprecated = isDeprecated;
    }


    public String differenceBetween(MethodInfo other) {

        if (!other.isDeprecated && isDeprecated) {
            return "[deprecated]";
        }

        return "";
    }

    @Override
    public String toString() {

        if (isDeprecated) {
            return "[deprecated]";
        }

        return "";
    }
}