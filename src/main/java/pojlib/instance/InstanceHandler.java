package pojlib.instance;

import android.app.Activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;
import pojlib.install.FabricMeta;
import pojlib.install.Installer;
import pojlib.install.MinecraftMeta;
import pojlib.install.QuiltMeta;
import pojlib.install.VersionInfo;
import pojlib.util.Constants;
import pojlib.util.ModInfo;
import pojlib.util.ModsJson;
import pojlib.util.DownloadUtils;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.VLoader;

public class InstanceHandler {
    public static final String MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/mods.json";
    public static final String DEV_MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/QuestCraft/devmods.json";

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
    public static MinecraftInstances.Instance create(Activity activity, String instanceName, String gameDir, MinecraftMeta.MinecraftVersion minecraftVersion, ModLoader modLoader, String modsFolderName) {
        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        instance.instanceName = instanceName;
        instance.versionName = minecraftVersion.id;
        instance.gameDir = new File(gameDir).getAbsolutePath();
        if(modsFolderName != null) {
            instance.modsDirName = modsFolderName;
        } else {
            instance.modsDirName = instance.versionName;
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

            MinecraftInstances instances;
            try {
                instances = GsonUtils.jsonFileToObject(gameDir + "/instances.json", MinecraftInstances.class);
            } catch (Exception e) {
                instances = new MinecraftInstances();
            }
            assert instances != null;
            instances.instances.add(instance);

            // Write instance to json file
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
            API_V1.finishedDownloading = true;
        }).start();

        return instance;
    }

    // Load an instance from json
    public static MinecraftInstances load(String gameDir) {
        return GsonUtils.jsonFileToObject(gameDir + "/instances.json", MinecraftInstances.class);
    }

    public static void addMod(MinecraftInstances instances, MinecraftInstances.Instance instance,
                              String gameDir, String name, String version, String url) {
        ModInfo info = new ModInfo();
        info.name = name;
        info.url = url;
        info.version = version;

        instance.mods.add(info);

        GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
    }

    public static boolean hasMod(MinecraftInstances.Instance instance, String name) {
        for(ModInfo info : instance.mods) {
            if(info.name.equals(name)) {
                return true;
            }
        }

        return false;
    }

    public static boolean removeMod(MinecraftInstances instances, MinecraftInstances.Instance instance, String gameDir, String name) {
        ModInfo oldInfo = null;
        for(ModInfo info : instance.mods) {
            if(info.name.equals(name)) {
                oldInfo = info;
                break;
            }
        }

        if(oldInfo != null) {
            instance.mods.remove(oldInfo);
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
        }

        return oldInfo != null;
    }

    // Return true if instance was deleted
    public static boolean delete(MinecraftInstances instances, MinecraftInstances.Instance instance, String gameDir) {
        instances.instances.remove(instance);
        GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);

        return true;
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        try {
            JREUtils.redirectAndPrintJRELog();
            VLoader.setAndroidInitInfo(activity);
            while(!API_V1.finishedDownloading);
            JREUtils.launchJavaVM(activity, instance.generateLaunchArgs(account), instance.versionName);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}