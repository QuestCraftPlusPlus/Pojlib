package pojlib;

import android.app.Activity;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;
import pojlib.install.FabricMeta;
import pojlib.install.Installer;
import pojlib.install.MinecraftMeta;
import pojlib.install.QuiltMeta;
import pojlib.install.VersionInfo;
import pojlib.util.Constants;
import pojlib.util.DownloadUtils;
import pojlib.util.FileUtil;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ModInfo;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.VLoader;
import pojlib.util.json.ModrinthIndexJson;

public class InstanceHandler {
    public static final String MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/mods.json";
    public static final String DEV_MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/devmods.json";

    public static MinecraftInstances.Instance create(Activity activity, MinecraftInstances instances, String instanceName, String userHome, ModLoader modLoader, String mrpackFilePath, String imageURL) throws IOException {
        File mrpackJson = new File(Constants.USER_HOME + "/instances/" + instanceName + "/setup/modrinth.index.json");

        mrpackJson.getParentFile().mkdirs();
        FileUtil.UnzipArchive(activity, mrpackFilePath, instanceName + ".mrpack", Constants.USER_HOME + "/instances/" + instanceName);

        ModrinthIndexJson index = GsonUtils.jsonFileToObject(mrpackJson.getAbsolutePath(), ModrinthIndexJson.class);
        if(index == null) {
            Logger.getInstance().appendToLog("Couldn't install the modpack with path " + mrpackJson.getAbsolutePath());
            return null;
        }

        MinecraftInstances.Instance instance = create(activity, instances, instanceName, userHome, false, index.dependencies.minecraft, modLoader, imageURL);
        new Thread(() -> {
            API_V1.finishedDownloading = false;
            for (ModrinthIndexJson.ModpackFile file : index.files) {
                if (file.path.contains("mods")) {
                    ArrayList<ModInfo> mods = Lists.newArrayList(instance.mods);
                    ModInfo info = new ModInfo();
                    info.slug = file.path
                            .replaceAll(".*\\/", "")
                            .replaceAll("\\..*", "");
                    info.version = "1.0.0";
                    info.download_link = file.downloads[0];
                    mods.add(info);
                    instance.mods = mods.toArray(new ModInfo[0]);
                }
                try {
                    API_V1.currentDownload = file.path;
                    DownloadUtils.downloadFile(file.downloads[0], new File(instance.gameDir, file.path));
                } catch (IOException e) {
                    Logger.getInstance().appendToLog("Couldn't install the modpack with path " + mrpackJson.getAbsolutePath());
                    Logger.getInstance().appendToLog(e.toString());
                }
            }
            API_V1.finishedDownloading = false;
            GsonUtils.objectToJsonFile(userHome + "/instances.json", instances);
        }).start();

        return instance;
    }

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
    public static MinecraftInstances.Instance create(Activity activity, MinecraftInstances instances, String instanceName, String gameDir, boolean useDefaultMods, String minecraftVersion, ModLoader modLoader, String imageURL) {
        API_V1.finishedDownloading = false;
        File instancesFile = new File(gameDir + "/instances.json");
        if (instancesFile.exists()) {
            for (MinecraftInstances.Instance instance : instances.instances) {
                if (instance.instanceName.equals(instanceName)) {
                    Logger.getInstance().appendToLog("Instance " + instanceName + " already exists! Using original instance.");
                    return instance;
                }
            }
        }

        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        instance.instanceName = instanceName;
        instance.instanceImageURL = imageURL;
        instance.versionName = minecraftVersion;
        instance.gameDir = Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_");
        instance.defaultMods = useDefaultMods;

        File gameDirFile = new File(instance.gameDir);
        if(!gameDirFile.exists()) {
            gameDirFile.mkdirs();
        }

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
        }

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        instance.mainClass = modLoaderVersionInfo.mainClass;

        // Install minecraft
        VersionInfo finalModLoaderVersionInfo = modLoaderVersionInfo;

        if(instances.instances == null) {
            instances.instances = new MinecraftInstances.Instance[0];
        }

        ArrayList<MinecraftInstances.Instance> instances1 = Lists.newArrayList(instances.instances);
        instances1.add(instance);
        instances.instances = instances1.toArray(new MinecraftInstances.Instance[0]);

        new Thread(() -> {
            try {
                String clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir);
                String minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir);
                String modLoaderClasspath = Installer.installLibraries(finalModLoaderVersionInfo, gameDir);
                String lwjgl = Installer.installLwjgl(activity);

                instance.classpath = clientClasspath + File.pathSeparator + minecraftClasspath + File.pathSeparator + modLoaderClasspath + File.pathSeparator + lwjgl;

                instance.assetsDir = Installer.installAssets(minecraftVersionInfo, gameDir, activity, instance);
            } catch (IOException e) {
                e.printStackTrace();
            }
            instance.assetIndex = minecraftVersionInfo.assetIndex.id;

            // Write instance to json file
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
            instance.updateMods(instances);

            API_V1.finishedDownloading = true;
            Logger.getInstance().appendToLog("Finished Downloading!");
        }).start();

        return instance;
    }

    // Load an instance from json
    public static MinecraftInstances load(String gameDir) {
        MinecraftInstances instances;
        try {
            instances = GsonUtils.jsonFileToObject(gameDir + "/instances.json", MinecraftInstances.class);
        } catch (Exception e) {
            instances = new MinecraftInstances();
            instances.instances = new MinecraftInstances.Instance[0];
        }
        if(instances == null) {
            instances = new MinecraftInstances();
            instances.instances = new MinecraftInstances.Instance[0];
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
        }

        return instances;
    }

    public static void addMod(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String version, String url) {
        ModInfo info = new ModInfo();
        info.slug = name;
        info.download_link = url;
        info.version = version;

        ArrayList<ModInfo> mods = Lists.newArrayList(instance.mods);
        mods.add(info);
        instance.mods = mods.toArray(mods.toArray(new ModInfo[0]));

        GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
    }

    public static boolean hasMod(MinecraftInstances.Instance instance, String name) {
        for(ModInfo info : instance.mods) {
            if(info.slug.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    public static boolean removeMod(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        ModInfo oldInfo = null;
        for(ModInfo info : instance.mods) {
            if(info.slug.equalsIgnoreCase(name)) {
                oldInfo = info;
                break;
            }
        }

        if(oldInfo != null) {
            // Delete the mod
            File modFile = new File(instance.gameDir + "/mods/" + name + ".jar");
            modFile.delete();

            ArrayList<ModInfo> mods = Lists.newArrayList(instance.mods);
            mods.remove(oldInfo);
            instance.mods = mods.toArray(mods.toArray(new ModInfo[0]));
            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        }

        return oldInfo != null;
    }

    // Return true if instance was deleted
    public static boolean delete(MinecraftInstances instances, MinecraftInstances.Instance instance) {
        File instanceDir = new File(instance.gameDir);
        instanceDir.delete();

        ArrayList<MinecraftInstances.Instance> instances1 = Lists.newArrayList(instances.instances);
        instances1.remove(instance);
        instances.instances = instances1.toArray(new MinecraftInstances.Instance[0]);
        GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);

        return true;
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        try {
            JREUtils.redirectAndPrintJRELog();
            VLoader.setAndroidInitInfo(activity);
            JREUtils.launchJavaVM(activity, instance.generateLaunchArgs(account), instance);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}