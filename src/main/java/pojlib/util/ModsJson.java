package pojlib.util;

import java.util.ArrayList;

public class ModsJson {
    public ArrayList<Version> versions;

    public static class Version {
        public String name;
        public ArrayList<ModInfo> coreMods;
        public ArrayList<ModInfo> defaultMods;
    }
}
