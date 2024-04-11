package pojlib.util;

public class ModsJson {
    public Version[] versions;

    public static class Version {
        public String name;
        public Mod[] coreMods;
        public Mod[] defaultMods;
    }

    public static class Mod {
        public String slug;
        public String version;
        public String download_link;
    }
}
