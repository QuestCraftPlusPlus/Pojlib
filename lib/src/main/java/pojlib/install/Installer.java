package pojlib.install;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pojlib.util.APIHandler;
import pojlib.util.Constants;
import pojlib.util.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

//This class reads data from a game version json and downloads its contents.
//This works for the base game as well as mod loaders
public class Installer {

    // Will only download library if it is missing, however it will overwrite if sha1 does not match the downloaded library
    // Returns the classpath of the downloaded libraries
    public static String downloadLibraries(VersionInfo versionInfo, String gameDir) throws IOException {
        StringJoiner classpath = new StringJoiner(File.pathSeparator);

        for (VersionInfo.Library library : versionInfo.libraries) {
            for (int i = 0; i < 5; i++) {
                if (i == 4) throw new RuntimeException(String.format("Library download of %s failed after 5 retries", library.name));

                File libraryFile;
                String sha1;

                //Null means mod lib, otherwise vanilla lib
                if (library.downloads == null) {
                    String path = parseLibraryNameToPath(library.name);
                    libraryFile = new File(gameDir + "/libraries/", path);
                    sha1 = APIHandler.getRaw(library.url + path + ".sha1");
                    if (!libraryFile.exists()) DownloadUtils.downloadFile(library.url + path, libraryFile);
                } else {
                    VersionInfo.Library.Artifact artifact = library.downloads.artifact;
                    libraryFile = new File(gameDir + "/libraries/", artifact.path);
                    sha1 = artifact.sha1;
                    if (!libraryFile.exists()) DownloadUtils.downloadFile(artifact.url, libraryFile);
                }

                if (DownloadUtils.compareSHA1(libraryFile, sha1)) {
                    classpath.add(libraryFile.getAbsolutePath());
                    break;
                }
            }
        }

        return classpath.toString();
    }

    //Only works on minecraft, not fabric, quilt, etc...
    //Will only download asset if it is missing
    public static void downloadAssets(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        JsonObject assets = APIHandler.getFullUrl(minecraftVersionInfo.assetIndex.url, JsonObject.class);

        for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
            VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
            String path = asset.hash.substring(0, 2) + "/" + asset.hash;
            File assetFile = new File(gameDir + "/assets/", path);
            if (!assetFile.exists()) DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + "/" + path, assetFile);
        }
    }

    public static void installJVM(String gameDir) {

    }


    //Used for mod libraries, vanilla is handled a different (tbh better) way
    private static String parseLibraryNameToPath(String libraryName) {
        String[] parts = libraryName.split(":");
        String location = parts[0].replace(".", "/");
        String name = parts[1];
        String version = parts[2];

        return String.format("%s/%s/%s/%s", location, name, version, name + "-" + version + ".jar");
    }
}