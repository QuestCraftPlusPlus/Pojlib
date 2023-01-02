package pojlib.modmanager.api;

import com.google.gson.annotations.SerializedName;

import pojlib.util.APIHandler;
import pojlib.util.Constants;
import pojlib.util.FileUtil;

import java.io.File;
import java.io.IOException;

public class Fabric {

    private static final APIHandler handler = new APIHandler("https://meta.fabricmc.net/v2");
    private static String fabricLoaderVersion; //Store so we don't need to ask the api every time

    public static class Version {
        @SerializedName("version")
        public String version;
        @SerializedName("stable")
        public boolean stable;
    }

    //Will only ask api first time called, return fabricLoadVersion var every next time - save on unneeded api calls
    public static String getLatestLoaderVersion()  {
        if (fabricLoaderVersion != null) return fabricLoaderVersion;

        Version[] versions = handler.get("versions/loader", Version[].class);
        if (versions != null) {
            for (Version version : versions) {
                if (version.stable) {
                    fabricLoaderVersion = version.version;
                    return version.version;
                }
            }
        }
        fabricLoaderVersion = "0.14.7"; //Known latest as backup
        return fabricLoaderVersion;
    }

    //Won't do anything if version is already installed
    public static void downloadJson(String gameVersion, String loaderVersion) {
        String profileName = String.format("%s-%s-%s", "fabric-loader", loaderVersion, gameVersion);
        File path = new File(Constants.MC_DIR + "/" + profileName);
        if (new File(path.getPath() + "/" + profileName + ".json").exists()) return;

        try {
            String json = APIHandler.getRaw(String.format(handler + "/versions/loader/%s/%s/profile/json", gameVersion, loaderVersion));
            if (json != null) {
                if (!path.exists()) path.mkdirs();
                FileUtil.write(path.getPath() + "/" + profileName + ".json", json.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
