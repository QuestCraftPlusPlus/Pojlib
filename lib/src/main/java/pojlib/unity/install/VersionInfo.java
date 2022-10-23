package pojlib.unity.install;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class VersionInfo {
    @SerializedName("assetIndex")
    public AssetIndex assetIndex;
    @SerializedName("libraries")
    public List<Library> libraries;

    public static class AssetIndex {
        @SerializedName("id")
        public String id;
        @SerializedName("sha1")
        public String sha1;
        @SerializedName("totalSize")
        public int totalSize;
        @SerializedName("url")
        public String url;
    }

    public static class Library {
        @SerializedName("downloads")
        public Downloads downloads;
        @SerializedName("name")
        public String name;
        @SerializedName("url")
        public String url;

        public static class Downloads {
            @SerializedName("artifact")
            public Artifact artifact;
        }

        public static class Artifact {
            @SerializedName("path")
            public String path;
            @SerializedName("sha1")
            public String sha1;
            @SerializedName("size")
            public int size;
            @SerializedName("url")
            public String url;
        }
    }

    public static class Asset {
        @SerializedName("hash")
        public String hash;
        @SerializedName("size")
        public int size;
    }
}
