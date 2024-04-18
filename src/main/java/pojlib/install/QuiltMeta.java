package pojlib.install;

import com.google.gson.annotations.SerializedName;
import pojlib.util.APIHandler;
import pojlib.util.Constants;

public class QuiltMeta {

    private static final APIHandler handler = new APIHandler(Constants.QUILT_META_URL);

    public static class QuiltVersion {
        @SerializedName("version")
        public String version;
    }

    public static QuiltVersion[] getVersions() {
        return handler.get("versions/loader", QuiltVersion[].class);
    }

    public static QuiltVersion getLatestVersion() {
        QuiltVersion[] versions = getVersions();
        if (versions != null) return versions[0];
        return null;
    }

    public static VersionInfo getVersionInfo(QuiltVersion quiltVersion, String minecraftVersion) {
        return handler.get(String.format("versions/loader/%s/%s/profile/json", minecraftVersion, quiltVersion.version), VersionInfo.class);
    }
}