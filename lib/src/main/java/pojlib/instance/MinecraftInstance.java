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
import pojlib.util.CustomMods;
import pojlib.util.DownloadUtils;
import pojlib.util.FileUtil;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.VLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class MinecraftInstance {
    public static final String MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/mods.json";
    public static final String DEV_MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/devmods.json";
    public static final String CUSTOM_MODS = "custom_mods.json";
    public static Activity context;
    public String versionName;
    public String versionType;
    public String classpath;
    public String gameDir;
    public String assetIndex;
    public String assetsDir;
    public String mainClass;

    //creates a new instance of a minecraft version, install game + mod loader, stores non login related launch info to json
    public static MinecraftInstance create(Activity activity, String instanceName, String gameDir, MinecraftMeta.MinecraftVersion minecraftVersion) throws IOException {
        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstance instance = new MinecraftInstance();
        instance.versionName = minecraftVersion.id;
        instance.gameDir = new File(gameDir).getAbsolutePath();

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestStableVersion();
        VersionInfo modLoaderVersionInfo =  FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
        instance.mainClass = modLoaderVersionInfo.mainClass;

        // Install minecraft
        new Thread(() -> {
            try {
                String clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir);
                String minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir);
                String modLoaderClasspath = Installer.installLibraries(modLoaderVersionInfo, gameDir);
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
        String path = gameDir + "/instances/" + instanceName + "/instance.json";
        return GsonUtils.jsonFileToObject(path, MinecraftInstance.class);
    }

    // Return true if instance was deleted
    public static boolean delete(String instanceName, String gameDir) throws IOException {
        if (API_V1.ignoreInstanceName) {
            return new File(gameDir + "/instances/" + instanceName).delete();
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when deleting instances.");
        } else {
            return new File(gameDir + "/instances/" + instanceName).delete();
        }

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

            if (API_V1.developerMods) {
                DownloadUtils.downloadFile(DEV_MODS, mods);
            } else { DownloadUtils.downloadFile(MODS, mods); }

            DownloadUtils.downloadFile(CUSTOM_MODS, mods);

            JsonObject obj = GsonUtils.jsonFileToObject(mods.getAbsolutePath(), JsonObject.class);
            JsonObject objOld = GsonUtils.jsonFileToObject(modsOld.getAbsolutePath(), JsonObject.class);

            ArrayList<String> versions = new ArrayList<>();
            ArrayList<String> downloads = new ArrayList<>();
            ArrayList<String> name = new ArrayList<>();

            JsonArray verMods = obj.getAsJsonArray(this.versionName);
            for (JsonElement verMod : verMods) {
                JsonObject object = verMod.getAsJsonObject();
                versions.add(object.get("version").getAsString());
                downloads.add(object.get("download_link").getAsString());
                name.add(object.get("slug").getAsString());
            }

            if(modsOld.exists()) {
                InputStream stream = Files.newInputStream(mods.toPath());
                int size = stream.available();
                byte[] buffer = new byte[size];
                stream.read(buffer);
                stream.close();
                FileUtil.write(modsOld.getAbsolutePath(), buffer);
                int i = 0;
                boolean downloadAll = !(new File(Constants.MC_DIR + "/mods/" + this.versionName).exists());
                for (String download : downloads) {
                    if(!Objects.equals(versions.get(i), ((JsonObject) objOld.getAsJsonArray(versionName).get(i)).getAsJsonPrimitive("version").getAsString()) || downloadAll) {
                        DownloadUtils.downloadFile(download, new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + name.get(i) + ".jar"));
                    }
                    i++;
                }
                mods.delete();
            } else {
                InputStream stream = Files.newInputStream(mods.toPath());
                int size = stream.available();
                byte[] buffer = new byte[size];
                stream.read(buffer);
                stream.close();
                FileUtil.write(modsOld.getAbsolutePath(), buffer);
                int i = 0;
                for (String download : downloads) {
                    DownloadUtils.downloadFile(download, new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + name.get(i) + ".jar"));
                    i++;
                }
                mods.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addCustomMod(String name, String version, String url) {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            CustomMods mods = new CustomMods();
            mods.instances = new CustomMods.InstanceMods[1];
            mods.instances[0].version = this.versionName;
            mods.instances[0].mods = new CustomMods.ModInfo[1];
            mods.instances[0].mods[0].name = name;
            mods.instances[0].mods[0].version = version;
            mods.instances[0].mods[0].url = url;

            GsonUtils.objectToJsonFile(customMods.getPath(), mods);
            return;
        }

        CustomMods mods = GsonUtils.jsonFileToObject(customMods.getPath(), CustomMods.class);
        for(CustomMods.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                ArrayList<CustomMods.ModInfo> modInfoArray = new ArrayList<>(Arrays.asList(instance.mods));
                CustomMods.ModInfo info = new CustomMods.ModInfo();
                info.name = name;
                info.version = version;
                info.url = url;
                modInfoArray.add(info);

                CustomMods.ModInfo[] infos = new CustomMods.ModInfo[modInfoArray.size()];
                infos = modInfoArray.toArray(infos);

                instance.mods = infos;
                GsonUtils.objectToJsonFile(customMods.getPath(), mods);
                break;
            }
        }
    }

    public boolean hasCustomMod(String name) {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            return false;
        }

        CustomMods mods = GsonUtils.jsonFileToObject(customMods.getPath(), CustomMods.class);
        for(CustomMods.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                for (CustomMods.ModInfo info : instance.mods) {
                    if(info.name.equals(name)) {
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    public boolean removeMod(String name) {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            return false;
        }

        CustomMods mods = GsonUtils.jsonFileToObject(customMods.getPath(), CustomMods.class);
        for(CustomMods.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                for (CustomMods.ModInfo info : instance.mods) {
                    if(info.name.equals(name)) {
                        ArrayList<CustomMods.ModInfo> modInfoArray = new ArrayList<>(Arrays.asList(instance.mods));
                        modInfoArray.remove(info);
                        GsonUtils.objectToJsonFile(customMods.getPath(), mods);
                        break;
                    }
                }
                break;
            }
        }
        return true;
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