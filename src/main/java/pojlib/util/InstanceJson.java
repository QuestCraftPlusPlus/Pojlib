package pojlib.util;

public class InstanceJson {
    public Instance[] instances;

    public static class Instance {
        public String instanceName;
        public String instanceVersion;
        public String modsDir;
        public ModInfo[] mods;
    }

    public static class ModInfo {
        public String name;
        public String version;
        public String url;
    }

    public void updateInstance() {

    }
}
