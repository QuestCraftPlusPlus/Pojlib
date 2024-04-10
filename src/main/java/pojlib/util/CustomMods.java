package pojlib.util;

public class CustomMods {
    public InstanceMods[] instances;

    public static class InstanceMods {
        public String name;
        public ModInfo[] mods;
    }

    public static class ModInfo {
        public String name;
        public String version;
        public String url;
    }
}
