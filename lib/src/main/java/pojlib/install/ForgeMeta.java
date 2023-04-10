package pojlib.install;

import com.google.gson.annotations.SerializedName;

import pojlib.util.APIHandler;
import pojlib.util.Constants;

public class ForgeMeta {

    private static final APIHandler handler = new APIHandler(Constants.FORGE_META_URL);

    public static class ForgeVersion {
        @SerializedName("version")
        public String version;
        @SerializedName("stable")
        public boolean stable;
    }

    public static ForgeMeta.ForgeVersion[] getVersions() {
        return handler.get("versions/loader", ForgeMeta.ForgeVersion[].class);
    }

    public static ForgeMeta.ForgeVersion getLatestStableVersion() {
        for (ForgeMeta.ForgeVersion version : getVersions()) {
            if (version.stable) return version;
        }
        return null;
    }

    public static VersionInfo getVersionInfo(ForgeMeta.ForgeVersion forgeVersion, MinecraftMeta.MinecraftVersion minecraftVersion) {
        return handler.get(String.format("versions/loader/%s/%s/profile/json", minecraftVersion.id, forgeVersion.version), VersionInfo.class);
    }
}
