package pojlib.install;

import com.google.gson.annotations.SerializedName;
import pojlib.util.APIHandler;
import pojlib.util.Constants;

public class FabricMeta {

    private static final APIHandler handler = new APIHandler(Constants.FABRIC_META_URL);

    public static class FabricVersion {
        @SerializedName("version")
        public String version;
        @SerializedName("stable")
        public boolean stable;
    }

    public static FabricVersion[] getVersions() {
        return handler.get("versions/loader", FabricVersion[].class);
    }

    public static FabricVersion getLatestStableVersion() {
        for (FabricVersion version : getVersions()) {
            if (version.stable) return version;
        }
        return null;
    }

    public static VersionInfo getVersionInfo(FabricVersion fabricVersion, MinecraftMeta.MinecraftVersion minecraftVersion) {
        return handler.get(String.format("versions/loader/%s/%s/profile/json", minecraftVersion.id, fabricVersion.version), VersionInfo.class);
    }
}