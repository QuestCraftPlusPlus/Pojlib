package pojlib.util.json;

import com.google.gson.annotations.SerializedName;

public class ModrinthIndexJson {
    public String versionId;
    public String name;
    public String summary;
    public ModpackFile[] files;
    public Dependencies dependencies;

    public static class ModpackFile {
        public String path;
        public String[] downloads;
        public int fileSize;
    }

    public static class Dependencies {
        public String minecraft;
        @SerializedName("fabric-loader")
        public String fabricLoader;
    }
}
