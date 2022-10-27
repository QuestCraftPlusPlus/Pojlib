package pojlib.install;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class VersionInfo {
    @SerializedName("id")
    public String id;
    @SerializedName("type")
    public String type;
    @SerializedName("assetIndex")
    public AssetIndex assetIndex;
    @SerializedName("downloads")
    public Downloads downloads;
    @SerializedName("libraries")
    public List<Library> libraries;
    @SerializedName("mainClass")
    public String mainClass;
    @SerializedName("arguments")
    public Arguments arguments;

    public static class AssetIndex {
        @SerializedName("id")
        public String id;
        @SerializedName("totalSize")
        public int totalSize;
        @SerializedName("url")
        public String url;
    }

    public static class Downloads {
        @SerializedName("client")
        public Client client;

        public static class Client {
            @SerializedName("sha1")
            public String sha1;
            @SerializedName("url")
            public String url;
        }
    }

    public static class Arguments {
        @SerializedName("game")
        public String[] game;
        @SerializedName("jvm")
        public String[] jvm;

        public static class ArgValue {
            @SerializedName("rules")
            public ArgRules[] rules;
            @SerializedName("value")
            public String value;

            public static class ArgRules {
                @SerializedName("action")
                public String action;
                @SerializedName("features")
                public String features;
                @SerializedName("os")
                public ArgOS os;

                public static class ArgOS {
                    @SerializedName("name")
                    public String name;
                    @SerializedName("version")
                    public String version;
                }
            }
        }
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
