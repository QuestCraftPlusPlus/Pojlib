package pojlib.util.json;

public class ModsJson {
    public Version[] versions;

    public static class Version {
        public String name;
        public ProjectInfo[] coreMods;
        public ProjectInfo[] defaultMods;
    }
}
