package pojlib.unity.installers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import pojlib.unity.util.APIHandler;
import pojlib.unity.util.Constants;
import pojlib.unity.util.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MinecraftInstaller {

    private static final APIHandler handler = new APIHandler(Constants.MOJANG_API_URL);

    public static class Version {
        @SerializedName("id")
        public String id;
        @SerializedName("sha1")
        public String sha1;
    }

    private static class VersionManifest {
        @SerializedName("versions")
        public List<Version> versions;
    }

    public static class VersionInfo {
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
        }

        public static class Library {
            @SerializedName("downloads")
            public Downloads downloads;
            @SerializedName("name")
            public String name;

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
    }

    private static class Asset {
        @SerializedName("hash")
        public String hash;
        @SerializedName("size")
        public int size;
    }

    public static List<Version> getMinecraftVersions() {
        VersionManifest versionManifest = handler.get("mc/game/version_manifest_v2.json", VersionManifest.class);
        if (versionManifest == null) return new ArrayList<>();
        return versionManifest.versions;
    }

    public static VersionInfo getVersionInfo(Version gameVersion) {
        return handler.get(String.format("v1/packages/%s/%s.json", gameVersion.sha1, gameVersion.id), VersionInfo.class);
    }

    public static void downloadLibraries(VersionInfo versionInfo, String gameDir) throws IOException {
        for (VersionInfo.Library library : versionInfo.libraries) {
            for (int i = 0; i < 5; i++) {
                if (i == 4) throw new RuntimeException(String.format("Library download %s failed after 5 retries", library.name));

                VersionInfo.Library.Artifact artifact = library.downloads.artifact;
                File artifactFile = new File(gameDir + "/libraries/", artifact.path);
                DownloadUtils.downloadFile(artifact.url, artifactFile);

                if (DownloadUtils.compareSHA1(artifactFile, library.downloads.artifact.sha1)) break;
            }
        }
    }

    public static void downloadAssets(VersionInfo versionInfo, String gameDir) throws IOException {
        JsonObject assets = handler.get(String.format("v1/packages/%s/%s.json", versionInfo.assetIndex.sha1, versionInfo.assetIndex.id), JsonObject.class);
        Gson gson = new Gson();

        for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
            Asset asset = gson.fromJson(entry.getValue(), Asset.class);
            System.out.println(entry.getValue());
            String path = asset.hash.substring(0, 2) + "/" + asset.hash;
            DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + path, new File(gameDir + "/assets/", path));
        }
    }
}