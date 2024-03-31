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
import pojlib.util.CoreMods;
import pojlib.util.CustomMods;
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

    public void updateOrDownloadMods() {
        API_V1.finishedDownloading = false;
        new Thread(() -> {
            try {
                File mods = new File(Constants.USER_HOME + "/mods-new.json");
                File modsOld = new File(Constants.USER_HOME + "/mods.json");
                File customMods = new File(Constants.USER_HOME + "/custom_mods.json");

                if (API_V1.developerMods) {
                    DownloadUtils.downloadFile(DEV_MODS, mods);
                } else { DownloadUtils.downloadFile(MODS, mods); }

                CustomMods customModsObj = GsonUtils.jsonFileToObject(customMods.getAbsolutePath(), CustomMods.class);
                CoreMods obj = GsonUtils.jsonFileToObject(mods.getAbsolutePath(), CoreMods.class);
                CoreMods objOld = GsonUtils.jsonFileToObject(modsOld.getAbsolutePath(), CoreMods.class);

                if(customMods.exists()) {
                    assert customModsObj != null;
                    for(CustomMods.InstanceMods instMods : customModsObj.instances) {
                        if(!instMods.version.equals(this.versionName)) {
                            continue;
                        }
                        for(CustomMods.ModInfo info : instMods.mods) {
                            API_V1.currentDownload = info.name;
                            DownloadUtils.downloadFile(info.url, new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + info.name + ".jar"));
                        }
                    }
                }
                boolean downloadAll = !(new File(Constants.MC_DIR + "/mods/" + this.versionName).exists());

                if(modsOld.exists()) {
                    for(CoreMods.Version version : objOld.versions) {
                        if(!version.name.equals(this.versionName)) {
                            continue;
                        }
                        for(CoreMods.Mod mod : version.mods) {
                            for(CoreMods.Version newVer : obj.versions) {
                                if (!newVer.name.equals(this.versionName)) {
                                    continue;
                                }
                                for(CoreMods.Mod newMod : newVer.mods) {
                                    if((!newMod.version.equals(mod.version) || downloadAll) && newMod.slug.equals(mod.slug)) {
                                        API_V1.currentDownload = newMod.slug;
                                        DownloadUtils.downloadFile(newMod.download_link, new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + newMod.slug + ".jar"));
                                    }
                                }
                            }
                        }
                    }
                } else {
                    for(CoreMods.Version version : obj.versions) {
                        if (!version.name.equals(this.versionName)) {
                            continue;
                        }
                        for(CoreMods.Mod mod : version.mods) {
                            API_V1.currentDownload = mod.slug;
                            DownloadUtils.downloadFile(mod.download_link, new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + mod.slug + ".jar"));
                        }
                    }
                }

                FileUtils.copyFile(mods, modsOld);
                mods.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }

            API_V1.finishedDownloading = true;
        }).start();
    }

    public void addCustomMod(String name, String version, String url) {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            CustomMods mods = new CustomMods();
            mods.instances = new CustomMods.InstanceMods[1];
            mods.instances[0] = new CustomMods.InstanceMods();
            mods.instances[0].version = this.versionName;
            mods.instances[0].mods = new CustomMods.ModInfo[1];
            mods.instances[0].mods[0] = new CustomMods.ModInfo();
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
                return;
            }
        }

        // If instance does not exist in file, create it
        ArrayList<CustomMods.InstanceMods> instanceInfo = new ArrayList<>(Arrays.asList(mods.instances));
        CustomMods.InstanceMods instMods = new CustomMods.InstanceMods();
        instMods.version = this.versionName;
        instMods.mods = new CustomMods.ModInfo[1];
        instMods.mods[0] = new CustomMods.ModInfo();
        instMods.mods[0].name = name;
        instMods.mods[0].version = version;
        instMods.mods[0].url = url;
        instanceInfo.add(instanceInfo.size(), instMods);

        // Set the array
        mods.instances = instanceInfo.toArray(new CustomMods.InstanceMods[0]);
        GsonUtils.objectToJsonFile(customMods.getAbsolutePath(), mods);
    }

    public boolean hasCustomMod(String name) throws IOException {
        File customMods = new File(Constants.MC_DIR, CUSTOM_MODS);
        if(!customMods.exists()) {
            return false;
        }

        CustomMods mods = GsonUtils.jsonFileToObject(customMods.getPath(), CustomMods.class);
        assert mods != null;
        for(CustomMods.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                for (CustomMods.ModInfo info : instance.mods) {
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

        CustomMods mods = GsonUtils.jsonFileToObject(customMods.getAbsolutePath(), CustomMods.class);
        assert mods != null;
        for(CustomMods.InstanceMods instance : mods.instances) {
            if(instance.version.equals(this.versionName)) {
                for (CustomMods.ModInfo info : instance.mods) {
                    if(info.name.equals(name)) {
                        ArrayList<CustomMods.ModInfo> modInfoArray = new ArrayList<>(Arrays.asList(instance.mods));
                        File mod = new File(Constants.MC_DIR + "/mods/" + this.versionName + "/" + info.name + ".jar");
                        mod.delete();
                        modInfoArray.remove(info);
                        instance.mods = modInfoArray.toArray(new CustomMods.ModInfo[0]);
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