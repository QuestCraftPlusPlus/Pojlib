package pojlib.install;

import com.google.gson.annotations.SerializedName;
import pojlib.util.APIHandler;
import pojlib.util.Constants;

public class MinecraftMeta {

    private static final APIHandler handler = new APIHandler(Constants.MOJANG_META_URL);

    public static class MinecraftVersion {
        @SerializedName("id")
        public String id;
        @SerializedName("sha1")
        public String sha1;
    }

    public static MinecraftVersion[] getVersions() {
        return handler.get("mc/game/version_manifest_v2.json", MinecraftVersion[].class);
    }

    public static VersionInfo getVersionInfo(MinecraftVersion minecraftVersion) {
        return handler.get(String.format("v1/packages/%s/%s.json", minecraftVersion.sha1, minecraftVersion.id), VersionInfo.class);
    }
}