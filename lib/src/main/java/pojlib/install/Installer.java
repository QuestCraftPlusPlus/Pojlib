package pojlib.install;

import android.app.Activity;
import android.content.Context;

import android.provider.Telephony;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;

import pojlib.instance.MinecraftInstance;
import pojlib.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.StringJoiner;

//This class reads data from a game version json and downloads its contents.
//This works for the base game as well as mod loaders
public class Installer extends Thread {

    // Will only download client if it is missing, however it will overwrite if sha1 does not match the downloaded client
    // Returns client classpath
    public static String installClient(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        Logger.getInstance().appendToLog("Downloading Client");

        File clientFile = new File(gameDir + "/versions/" + minecraftVersionInfo.id + "/" + minecraftVersionInfo.id + ".jar");
        for (int i = 0; i < 5; i++) {
            if (i == 4) throw new RuntimeException("Client download failed after 5 retries");

            if (!clientFile.exists()) DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile);
            if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) return clientFile.getAbsolutePath();
        }
        return null;
    }

    // Will only download library if it is missing, however it will overwrite if sha1 does not match the downloaded library
    // Returns the classpath of the downloaded libraries
    public static String installLibraries(VersionInfo versionInfo, String gameDir) throws IOException {
        Logger.getInstance().appendToLog("Downloading Libraries for: " + versionInfo.id);

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
                    if (!libraryFile.exists()) {
                        Logger.getInstance().appendToLog("Downloading: " + library.name);
                        DownloadUtils.downloadFile(library.url + path, libraryFile);
                    }
                } else {
                    VersionInfo.Library.Artifact artifact = library.downloads.artifact;
                    libraryFile = new File(gameDir + "/libraries/", artifact.path);
                    sha1 = artifact.sha1;
                    if (!libraryFile.exists() && !artifact.path.contains("lwjgl")) {
                        Logger.getInstance().appendToLog("Downloading: " + library.name);
                        DownloadUtils.downloadFile(artifact.url, libraryFile);
                    }
                }

                if (DownloadUtils.compareSHA1(libraryFile, sha1)) {
                    // Add our GLFW
                    classpath.add(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");

                    classpath.add(libraryFile.getAbsolutePath());
                    break;
                }
            }
        }

        return classpath.toString();
    }

    //Only works on minecraft, not fabric, quilt, etc...
    //Will only download asset if it is missing
    public static String installAssets(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        Logger.getInstance().appendToLog("Downloading assets");
        JsonObject assets = APIHandler.getFullUrl(minecraftVersionInfo.assetIndex.url, JsonObject.class);

        for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
            AsyncDownload thread = new AsyncDownload(entry);
            thread.start();
        }

        DownloadUtils.downloadFile(minecraftVersionInfo.assetIndex.url, new File(gameDir + "/assets/indexes/" + minecraftVersionInfo.assets + ".json"));

        FileUtils.writeByteArrayToFile(new File(Constants.MC_DIR + "/config/sodium-extra.properties"), FileUtil.loadFromAssetToByte(MinecraftInstance.context, "sodium-extra.properties"));
        FileUtils.writeByteArrayToFile(new File(Constants.MC_DIR + "/config/sodium-mixins.properties"), FileUtil.loadFromAssetToByte(MinecraftInstance.context, "sodium-mixins.properties"));
        FileUtils.writeByteArrayToFile(new File(Constants.MC_DIR + "/config/vivecraft-config.properties"), FileUtil.loadFromAssetToByte(MinecraftInstance.context, "vivecraft-config.properties"));
        FileUtils.writeByteArrayToFile(new File(Constants.MC_DIR + "/options.txt"), FileUtil.loadFromAssetToByte(MinecraftInstance.context, "options.txt"));
        FileUtils.writeByteArrayToFile(new File(Constants.MC_DIR + "/servers.dat"), FileUtil.loadFromAssetToByte(MinecraftInstance.context, "servers.dat"));

        return new File(gameDir + "/assets").getAbsolutePath();
    }

    public static class AsyncDownload extends Thread {
        Map.Entry<String, JsonElement> entry;

        public void run(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
            VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
            String path = asset.hash.substring(0, 2) + "/" + asset.hash;
            File assetFile = new File(gameDir + "/assets/objects/", path);

            while (Installer.AsyncDownload.activeCount() >= 5) {

            }   if (!assetFile.exists()) {
                    Logger.getInstance().appendToLog("Downloading: " + entry.getKey());
                    DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + "/" + path, assetFile);
                }
        }

        public AsyncDownload( Map.Entry<String, JsonElement> entry) {
            this.entry = entry;
        }
    }

    public static String installLwjgl(Activity activity) throws IOException {
        File lwjgl = new File(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes-3.2.3.jar");
        if (!lwjgl.exists()) {
            lwjgl.getParentFile().mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), FileUtil.loadFromAssetToByte(activity, "lwjgl/lwjgl-glfw-classes-3.2.3.jar"));
        }
        return lwjgl.getAbsolutePath();
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