package pojlib.install;

import android.app.Activity;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;

import pojlib.APIHandler;

import pojlib.util.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.StringJoiner;

//This class reads data from a game version json and downloads its contents.
//This works for the base game as well as mod loaders
public class Installer {

    public static void installJVM(Activity activity) {
        Logger.getInstance().appendToLog("Checking JRE");
        File jre = new File(activity.getFilesDir(), "runtimes/JRE");
        String jreURL = "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE.zip";

        try {
            if (!jre.exists()) {
                Logger.getInstance().appendToLog("Installing JRE");
                File jreZip = new File(activity.getFilesDir() + "/runtimes/JRE.zip");
                DownloadUtils.downloadFile(jreURL, jreZip, true, activity);
                FileUtil.unzipArchive(jreZip.getPath(), activity.getFilesDir() + "/runtimes/JRE");
                Files.copy(Paths.get(activity.getApplicationInfo().nativeLibraryDir + "/libawt_xawt.so"), Paths.get(activity.getFilesDir() + "/runtimes/JRE/lib/libawt_xawt.so"));
                jreZip.delete();
            }

            Logger.getInstance().appendToLog("JRE installed");
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Failed to install JRE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Will only download client if it is missing, however it will overwrite if sha1 does not match the downloaded client
    // Returns client classpath
    public static String installClient(VersionInfo minecraftVersionInfo, String gameDir, Activity activity) throws IOException {
        Logger.getInstance().appendToLog("Checking Client");

        File clientFile = new File(gameDir + "/versions/" + minecraftVersionInfo.id + "/" + minecraftVersionInfo.id + ".jar");

        try {
            if (!clientFile.exists()) {
                DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile, true, activity);
            } else if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) {
                clientFile.delete();
                DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile, true, activity);
            }

            // Check if the downloaded client matches the expected SHA1 hash
            if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) {
                Logger.getInstance().appendToLog("Client downloaded");
                return clientFile.getAbsolutePath();
            }
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Failed to download client: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // Will only download library if it is missing, however it will overwrite if sha1 does not match the downloaded library
    // Returns the classpath of the downloaded libraries
    public static String installLibraries(VersionInfo versionInfo, String gameDir, Activity activity) throws IOException {
        Logger.getInstance().appendToLog("Checking Libraries for: " + versionInfo.id);
        StringJoiner classpath = new StringJoiner(File.pathSeparator);

        for (VersionInfo.Library library : versionInfo.libraries) {
            if(library.name.contains("lwjgl") || (library.name.contains("org.ow2.asm")) & !versionInfo.id.contains("fabric")) {
                continue;
            }

            File libraryFile;
            String sha1;

            //Null means mod lib, otherwise vanilla lib
            if (library.downloads == null) {
                String path = parseLibraryNameToPath(library.name);
                libraryFile = new File(gameDir + "/libraries/", path);
                sha1 = APIHandler.getRaw(library.url + path + ".sha1");
                if (!libraryFile.exists()) {
                    Logger.getInstance().appendToLog("Downloading: " + library.name);
                    DownloadUtils.downloadFile(library.url + path, libraryFile, false, activity);
                }
            } else {
                VersionInfo.Library.Artifact artifact = library.downloads.artifact;
                libraryFile = new File(gameDir + "/libraries/", artifact.path);
                sha1 = artifact.sha1;
                if (!libraryFile.exists()) {
                    Logger.getInstance().appendToLog("Downloading: " + library.name);
                    DownloadUtils.downloadFile(artifact.url, libraryFile, false, activity);
                }
            }

            if(DownloadUtils.compareSHA1(libraryFile, sha1)) {
                classpath.add(libraryFile.getAbsolutePath());
                break;
            }
        }

        // Add our GLFW
        classpath.add(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");
        // DNS SRV Resolver fix
        classpath.add(Constants.USER_HOME + "/hacks/ResConfHack.jar");

        Logger.getInstance().appendToLog("Libraries installed");
        return classpath.toString();
    }

    //Only works on minecraft, not fabric, quilt, etc...
    //Will only download asset if it is missing
    // TODO: Make sure I didn't break the async checks
    public static String installAssets(VersionInfo minecraftVersionInfo, String gameDir, Activity activity) throws IOException {
        Logger.getInstance().appendToLog("Checking assets");
        JsonObject assets = APIHandler.getFullUrl(minecraftVersionInfo.assetIndex.url, JsonObject.class);

        for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
            VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
            String path = asset.hash.substring(0, 2) + "/" + asset.hash;
            File assetFile = new File(gameDir + "/assets/objects/", path);

            if (!assetFile.exists()) {
                Logger.getInstance().appendToLog("Downloading: " + entry.getKey());
                try {
                    DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + "/" + path, assetFile, true, activity);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (DownloadUtils.compareSHA1(assetFile, asset.hash)) {
                break;
            } else {
                assetFile.delete();
            }
        }

        File indexJson = new File(gameDir + "/assets/indexes/" + minecraftVersionInfo.assets + ".json");
        if (!indexJson.exists()) DownloadUtils.downloadFile(minecraftVersionInfo.assetIndex.url, indexJson, true, activity);

        return new File(gameDir + "/assets").getAbsolutePath();
    }

    public static void moveLocalAssets(Activity activity, MinecraftInstances.Instance instance) throws IOException {
        try {
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/sodium-options.json"), FileUtil.loadFromAssetToByte(activity, "sodium-options.json"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/smoothboot.json"), FileUtil.loadFromAssetToByte(activity, "smoothboot.json"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/immediatelyfast.json"), FileUtil.loadFromAssetToByte(activity, "immediatelyfast.json"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/moreculling.toml"), FileUtil.loadFromAssetToByte(activity,"moreculling.toml"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/modernfix-mixins.properties"), FileUtil.loadFromAssetToByte(activity,"modernfix-mixins.properties"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/options.txt"), FileUtil.loadFromAssetToByte(activity, "options.txt"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/servers.dat"), FileUtil.loadFromAssetToByte(activity, "servers.dat"));
            FileUtils.writeByteArrayToFile(new File(Constants.USER_HOME + "/hacks/ResConfHack.jar"), FileUtil.loadFromAssetToByte(activity, "hacks/ResConfHack.jar"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/vivecraft-client-config.json"), FileUtil.loadFromAssetToByte(activity, "vivecraft-client-config.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
