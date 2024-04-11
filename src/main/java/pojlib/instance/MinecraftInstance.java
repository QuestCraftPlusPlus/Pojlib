package pojlib.instance;

import android.app.Activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;
import pojlib.install.FabricMeta;
import pojlib.install.Installer;
import pojlib.install.MinecraftMeta;
import pojlib.install.QuiltMeta;
import pojlib.install.VersionInfo;
import pojlib.util.Constants;
import pojlib.util.ModsJson;
import pojlib.util.InstanceJson;
import pojlib.util.DownloadUtils;
import pojlib.util.FileUtil;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.VLoader;

public class MinecraftInstance {
    public static final String MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/mods.json";
    public static final String DEV_MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/devmods.json";
    public static final String CUSTOM_MODS = "custom_mods.json";
    public String versionName;
    public String instanceName;
    public String versionType;
    public String classpath;
    public String gameDir;
    public String assetIndex;
    public String assetsDir;
    public String mainClass;

    public enum ModLoader {
        Fabric(0),
        Quilt(1),
        Forge(2),
        NeoForge(3);

        public final int index;

        ModLoader(int i) {
            this.index = i;
        }
    }

    //creates a new instance of a minecraft version, install game + mod loader, stores non login related launch info to json
    public static MinecraftInstance create(Activity activity, String instanceName, String gameDir, MinecraftMeta.MinecraftVersion minecraftVersion, ModLoader modLoader) {
        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstance instance = new MinecraftInstance();
        instance.instanceName = instanceName;
        instance.versionName = minecraftVersion.id;
        instance.gameDir = new File(gameDir).getAbsolutePath();

        VersionInfo modLoaderVersionInfo = null;
        switch (modLoader) {
            case Fabric: {
                FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestStableVersion();
                assert fabricVersion != null;
                modLoaderVersionInfo = FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
                break;
            }
            case Quilt: {
                QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
                assert quiltVersion != null;
                modLoaderVersionInfo = QuiltMeta.getVersionInfo(quiltVersion, minecraftVersion);
                break;
            }
            case Forge:
            case NeoForge:
            {
                System.out.println("Error!: You cannot use Forge or NeoForge with QuestCraft!");
                break;
            }
        }

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        instance.mainClass = modLoaderVersionInfo.mainClass;

        // Install minecraft
        VersionInfo finalModLoaderVersionInfo = modLoaderVersionInfo;
        new Thread(() -> {
            try {
                String clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir);
                String minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir);
                String modLoaderClasspath = Installer.installLibraries(finalModLoaderVersionInfo, gameDir);
                String lwjgl = Installer.installLwjgl(activity);

                instance.classpath = clientClasspath + File.pathSeparator + minecraftClasspath + File.pathSeparator + modLoaderClasspath + File.pathSeparator + lwjgl;

                instance.assetsDir = Installer.installAssets(minecraftVersionInfo, gameDir, activity);
            } catch (IOException e) {
                e.printStackTrace();
            }
            instance.assetIndex = minecraftVersionInfo.assetIndex.id;

            // Write instance to json file
            GsonUtils.objectToJsonFile(gameDir + "/instances/" + instanceName + "/instance.json", instance);
            API_V1.finishedDownloading = true;
        }).start();

        updateInstancesJson(gameDir, instance);
        return instance;
    }

    private static synchronized void updateInstancesJson(String gameDir, MinecraftInstance instance) {
        String instancesFilePath = gameDir + "/instances.json";
        JsonArray instancesArray;

        try {
            if (new File(instancesFilePath).exists()) {
                String jsonContent = FileUtil.read(instancesFilePath);
                instancesArray = GsonUtils.GLOBAL_GSON.fromJson(jsonContent, JsonArray.class);
            } else {
                instancesArray = new JsonArray();
            }

            JsonObject instancesJson = new JsonObject();
            instancesJson.addProperty("instanceName", instance.instanceName);
            instancesJson.addProperty("instanceVersion", instance.versionName);
            instancesJson.addProperty("gameDir", instance.gameDir);


            instancesArray.add(instancesJson);

            GsonUtils.objectToJsonFile(instancesFilePath, instancesArray);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public void updateOrDownloadMods() throws Exception{
        API_V1.finishedDownloading = false;
        new Thread(() -> {
             File coreMods = new File(Constants.USER_HOME + "/coreMods.json");
            File instances = new File(Constants.USER_HOME + "/instances.json");

            if (API_V1.developerMods) {
                DownloadUtils.downloadFile(DEV_MODS, coreMods);
            } else {
                DownloadUtils.downloadFile(MODS, coreMods);
            }

            CustomModsJson customModsObj = GsonUtils.jsonFileToObject(customMods.getAbsolutePath(), CustomModsJson.class);
            ModsJson obj = GsonUtils.jsonFileToObject(mods.getAbsolutePath(), ModsJson.class);

            API_V1.finishedDownloading = true;
        }).start();
    }

    public void addCustomMod(String name, String version, String url) {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            CustomModsJson mods = new CustomModsJson();
            mods.instances = new CustomModsJson.InstanceMods[1];
            mods.instances[0] = new CustomModsJson.InstanceMods();
            mods.instances[0].version = this.versionName;
            mods.instances[0].mods = new CustomModsJson.ModInfo[1];
            mods.instances[0].mods[0] = new CustomModsJson.ModInfo();
            mods.instances[0].mods[0].name = name;
            mods.instances[0].mods[0].version = version;
            mods.instances[0].mods[0].url = url;

            GsonUtils.objectToJsonFile(customMods.getPath(), mods);
            return;
        }

        CustomModsJson mods = GsonUtils.jsonFileToObject(customMods.getPath(), CustomModsJson.class);
        for(CustomModsJson.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                ArrayList<CustomModsJson.ModInfo> modInfoArray = new ArrayList<>(Arrays.asList(instance.mods));
                CustomModsJson.ModInfo info = new CustomModsJson.ModInfo();
                info.name = name;
                info.version = version;
                info.url = url;
                modInfoArray.add(info);

                CustomModsJson.ModInfo[] infos = new CustomModsJson.ModInfo[modInfoArray.size()];
                infos = modInfoArray.toArray(infos);

                instance.mods = infos;
                GsonUtils.objectToJsonFile(customMods.getPath(), mods);
                return;
            }
        }

        // If instance does not exist in file, create it
        ArrayList<CustomModsJson.InstanceMods> instanceInfo = new ArrayList<>(Arrays.asList(mods.instances));
        CustomModsJson.InstanceMods instMods = new CustomModsJson.InstanceMods();
        instMods.version = this.versionName;
        instMods.mods = new CustomModsJson.ModInfo[1];
        instMods.mods[0] = new CustomModsJson.ModInfo();
        instMods.mods[0].name = name;
        instMods.mods[0].version = version;
        instMods.mods[0].url = url;
        instanceInfo.add(instanceInfo.size(), instMods);

        // Set the array
        mods.instances = instanceInfo.toArray(new CustomModsJson.InstanceMods[0]);
        GsonUtils.objectToJsonFile(customMods.getAbsolutePath(), mods);
    }

    public boolean hasCustomMod(String name) throws IOException {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            return false;
        }

        CustomModsJson mods = GsonUtils.jsonFileToObject(customMods.getPath(), CustomModsJson.class);
        assert mods != null;
        for(CustomModsJson.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                for (CustomModsJson.ModInfo info : instance.mods) {
                    // Check if core mod is already included
                    File modsOld = new File(Constants.USER_HOME + "/mods.json");
                    if(!modsOld.exists()) {
                        if (API_V1.developerMods) {
                            DownloadUtils.downloadFile(DEV_MODS, modsOld);
                        } else { DownloadUtils.downloadFile(MODS, modsOld); }
                    }
                    JsonObject objOld = GsonUtils.jsonFileToObject(modsOld.getAbsolutePath(), JsonObject.class);
                    assert objOld != null;
                    for (JsonElement verMod : objOld.getAsJsonArray(this.versionName)) {
                        JsonObject object = verMod.getAsJsonObject();
                        String slug = object.get("slug").getAsString();
                        if(name.equalsIgnoreCase(slug)) {
                            return true;
                        }
                    }
                    if(info.name.equalsIgnoreCase(name)) {
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

        // Check if core mod is already included, if so, don't delete
        File modsOld = new File(Constants.USER_HOME + "/mods.json");
        if(modsOld.exists()) {
            JsonObject objOld = GsonUtils.jsonFileToObject(modsOld.getAbsolutePath(), JsonObject.class);
            assert objOld != null;
            for (JsonElement verMod : objOld.getAsJsonArray(this.versionName)) {
                JsonObject object = verMod.getAsJsonObject();
                String slug = object.get("slug").getAsString().replace("-", " ");
                if(name.equals(slug)) {
                    return false;
                }
            }
        }

        CustomModsJson mods = GsonUtils.jsonFileToObject(customMods.getAbsolutePath(), CustomModsJson.class);
        assert mods != null;
        for(CustomModsJson.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                for (CustomModsJson.ModInfo info : instance.mods) {
                    if(info.name.equals(name)) {
                        ArrayList<CustomModsJson.ModInfo> modInfoArray = new ArrayList<>(Arrays.asList(instance.mods));
                        File mod = new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + info.name + ".jar");
                        mod.delete();
                        modInfoArray.remove(info);
                        instance.mods = modInfoArray.toArray(new CustomModsJson.ModInfo[0]);
                        GsonUtils.objectToJsonFile(customMods.getAbsolutePath(), mods);
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    public void launchInstance(Activity activity, MinecraftAccount account) {
        try {
            JREUtils.redirectAndPrintJRELog();
            VLoader.setAndroidInitInfo(activity);
            while(!API_V1.finishedDownloading);
            JREUtils.launchJavaVM(activity, generateLaunchArgs(account), versionName);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}