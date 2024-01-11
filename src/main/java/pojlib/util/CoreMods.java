package pojlib.util;

import java.util.List;

public class CoreMods {
    public Version[] versions;

    public static class Version {
        public String name;
        public Mod[] mods;
    }

    public static class Mod {
        public String slug;
        public String version;
        public String download_link;
    }
}
