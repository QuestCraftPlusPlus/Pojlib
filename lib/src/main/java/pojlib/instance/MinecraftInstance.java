package pojlib.instance;

import android.app.Activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import pojlib.UnityPlayerActivity;
import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;
import pojlib.install.*;
import pojlib.util.Constants;
import pojlib.util.DownloadUtils;
import pojlib.util.FileUtil;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.VLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MinecraftInstance {

    public static final String MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/mods.json";
    public static Activity context;
    public String versionName;
    public String versionType;
    public String classpath;
    public String gameDir;
    public String assetIndex;
    public String assetsDir;
    public String mainClass;

    //WIP!!!!!!
    //creates a new instance of a minecraft version, install game + mod loader, stores non login related launch info to json
    public static MinecraftInstance create(Activity activity, String instanceName, String gameDir, MinecraftMeta.MinecraftVersion minecraftVersion, int modLoader) throws IOException {
        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstance instance = new MinecraftInstance();
        instance.versionName = minecraftVersion.id;
        instance.gameDir = new File(gameDir).getAbsolutePath();

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestStableVersion();
        VersionInfo modLoaderVersionInfo =  FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
        instance.mainClass = modLoaderVersionInfo.mainClass;

        // Get mod loader info
        if (modLoader == 0) {
            instance.mainClass = minecraftVersionInfo.mainClass;
        } else if (modLoader == 1) {
            if (fabricVersion != null) {
                modLoaderVersionInfo = FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
                instance.mainClass = modLoaderVersionInfo.mainClass;
            }
        } else if (modLoader == 2) {
            QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
            if (quiltVersion != null) {
                modLoaderVersionInfo = QuiltMeta.getVersionInfo(quiltVersion, minecraftVersion);
                instance.mainClass = modLoaderVersionInfo.mainClass;
            }
        } else if (modLoader == 3) {
            throw new RuntimeException("Forge not yet implemented\nExiting...");
        }

        if (modLoaderVersionInfo == null) throw new RuntimeException("Error fetching mod loader data");

        // Install minecraft
        VersionInfo finalModLoaderVersionInfo = modLoaderVersionInfo;
        new Thread(() -> {
            try {
                String clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir);
                String minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir);
                String modLoaderClasspath = Installer.installLibraries(finalModLoaderVersionInfo, gameDir);
                String lwjgl = Installer.installLwjgl(activity);

                instance.classpath = clientClasspath + File.pathSeparator + minecraftClasspath + File.pathSeparator + modLoaderClasspath + File.pathSeparator + lwjgl;

                instance.assetsDir = Installer.installAssets(minecraftVersionInfo, gameDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
            instance.assetIndex = minecraftVersionInfo.assetIndex.id;

            // Write instance to json file
            GsonUtils.objectToJsonFile(gameDir + "/instances/" + instanceName + "/instance.json", instance);
            API_V1.finishedDownloading = true;
        }).start();
        return instance;
    }

    // Load an instance from json
    public static MinecraftInstance load(String instanceName, String gameDir) {
        return GsonUtils.jsonFileToObject(gameDir + "/instances/" + instanceName + "/instance.json", MinecraftInstance.class);
    }

    // Return true if instance was deleted
    public static boolean delete(String instanceName, String gameDir) {
        return new File(gameDir + "/instances/" + instanceName).delete();
    }

    public List<String> generateLaunchArgs(MinecraftAccount account) {
        String[] mcArgs = {"--username", account.username, "--version", versionName, "--gameDir", gameDir,
                "--assetsDir", assetsDir, "--assetIndex", assetIndex, "--uuid", account.uuid.replaceAll("-", ""),
                "--accessToken", account.accessToken, "--userType", account.userType, "--versionType", versionType};

        List<String> allArgs = new ArrayList<>(Arrays.asList("-cp", classpath));
        allArgs.add(mainClass);
        allArgs.addAll(Arrays.asList(mcArgs));
        return allArgs;
    }

    public void updateOrDownloadsMods() {
        try {
            File mods = new File(Constants.USER_HOME + "/mods-new.json");
            File modsOld = new File(Constants.USER_HOME + "/mods.json");
            DownloadUtils.downloadFile(MODS, mods);
            JsonObject obj = GsonUtils.jsonFileToObject(mods.getAbsolutePath(), JsonObject.class);
            JsonObject objOld = GsonUtils.jsonFileToObject(modsOld.getAbsolutePath(), JsonObject.class);

            ArrayList<String> versions = new ArrayList<>();
            ArrayList<String> downloads = new ArrayList<>();
            ArrayList<String> name = new ArrayList<>();

            if(mods.exists()) {
                JsonArray verMods = obj.getAsJsonArray(this.versionName);
                for (JsonElement verMod : verMods) {
                    JsonObject object = verMod.getAsJsonObject();
                    versions.add(object.get("version").getAsString());
                    downloads.add(object.get("download_link").getAsString());
                    name.add(object.get("slug").getAsString());
                }
            }

            if(!modsOld.exists()) {
                JsonArray verMods = obj.getAsJsonArray(this.versionName);
                for (JsonElement verMod : verMods) {
                    JsonObject object = verMod.getAsJsonObject();
                    for (String version : versions) {
                        if (!version.equals(object.get("version").getAsString())) {
                            InputStream stream = new FileInputStream(mods);
                            int size = stream.available();
                            byte[] buffer = new byte[size];
                            stream.read(buffer);
                            stream.close();
                            FileUtil.write(modsOld.getAbsolutePath(), buffer);

                            DownloadUtils.downloadFile(object.getAsJsonObject("download_link").getAsString(), new File(Constants.MC_DIR + "/mods/" + this.versionName  + "/" + object.get("slug").getAsString() + ".jar"));
                            break;
                        }
                    }
                }
                mods.delete();
            } else {
                InputStream stream = new FileInputStream(mods);
                int size = stream.available();
                byte[] buffer = new byte[size];
                stream.read(buffer);
                stream.close();
                FileUtil.write(modsOld.getAbsolutePath(), buffer);
                int i = 0;
                for (String download : downloads) {
                    DownloadUtils.downloadFile(download, new File(Constants.MC_DIR + "/mods/" + this.versionName  + "/" + name.get(i) + ".jar"));
                    i++;
                }
                mods.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void launchInstance(Activity activity, MinecraftAccount account) {
        try {
            updateOrDownloadsMods();
            JREUtils.redirectAndPrintJRELog();
            VLoader.setAndroidInitInfo(context);
            VLoader.setEGLGlobal(JREUtils.getEGLContextPtr(), JREUtils.getEGLDisplayPtr(), JREUtils.getEGLConfigPtr());
            JREUtils.launchJavaVM(activity, generateLaunchArgs(account), versionName);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}