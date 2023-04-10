package pojlib.modmanager;

import android.os.Build;
import android.util.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pojlib.modmanager.State.Instance;
import pojlib.modmanager.api.*;
import pojlib.util.DownloadUtils;
import pojlib.util.FileUtil;
import pojlib.util.GsonUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class ModManager {

    public static final String workDir = FileUtil.DIR_GAME_NEW + "/modmanager";
    public static State state = new State();
    private static final File modsJson = new File(workDir + "/mods.json");
    private static JsonObject modrinthCompat = new JsonObject();
    private static final ArrayList<String> currentDownloadSlugs = new ArrayList<>();
    private static boolean saveStateCalled = false;

    public static void init() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    JsonObject modManagerJson = GsonUtils.GLOBAL_GSON.fromJson(FileUtil.read(workDir + "/modmanager.json"), JsonObject.class);
                    modrinthCompat = GsonUtils.GLOBAL_GSON.fromJson(FileUtil.read(workDir + "/modrinth-compat.json"), JsonObject.class);

                    JsonArray repoList = modManagerJson.getAsJsonArray("repos");

                    //Init outside to cache version (see Fabric/Quilt.java)
                    String flVersion = Fabric.getLatestLoaderVersion();

                    if (!modsJson.exists()) {
                        state.fabricLoaderVersion = flVersion;
                        String gameVersion = DownloadUtils.getCompatibleVersions("releases").get(0);
                        Fabric.downloadJson(gameVersion, flVersion);
                        String fabricLoaderName = String.format("%s-%s-%s", "fabric-loader", flVersion, gameVersion);
                        Instance instance = new Instance();
                        instance.setName(fabricLoaderName);
                        instance.setGameVersion(gameVersion);
                        instance.setLoaderVersion(fabricLoaderName);
                        state.addInstance(instance);

                        gameVersion = DownloadUtils.getCompatibleVersions("releases").get(1);
                        Fabric.downloadJson(gameVersion, flVersion);
                        fabricLoaderName = String.format("%s-%s-%s", "fabric-loader", flVersion, gameVersion);
                        instance = new Instance();
                        instance.setName(fabricLoaderName);
                        instance.setGameVersion(gameVersion);
                        instance.setLoaderVersion(fabricLoaderName);
                        state.addInstance(instance);

                        GsonUtils.objectToJsonFile(modsJson.getPath(), GsonUtils.GLOBAL_GSON.toJson(state)); //Cant use save state cause async issues
                    } else state = GsonUtils.GLOBAL_GSON.fromJson(FileUtil.read(modsJson.getPath()), pojlib.modmanager.State.class);

                    //Remove mod metadata if they were deleted manually
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) return;
                    for (Instance instance : state.getInstances()) {
                        ArrayList<String> purgeList = new ArrayList<>();
                        File[] modFiles = new File(workDir + "/" + instance.getName()).listFiles();
                        if (modFiles == null) {
                            for (ModData mod : instance.getMods()) purgeList.add(mod.slug);
                            continue;
                        }

                        for (ModData mod : instance.getMods()) {
                            boolean foundMod = false;
                            for (File modFile : modFiles) {
                                if (modFile.getName().equals(mod.fileData.filename)) {
                                    foundMod = true;
                                    break;
                                }
                            }
                            if (!foundMod) purgeList.add(mod.slug);
                        }
                        instance.getMods().removeIf(mod -> purgeList.contains(mod.slug));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public static String getModCompat(String platform, String name) {
        JsonElement compatLevel = null;
        if (platform.equals("modrinth")) compatLevel = modrinthCompat.get(name);

        if (compatLevel != null) return compatLevel.getAsString();
        return "Untested";
    }

    public static Instance getInstance(String name) {
        Instance instance = state.getInstance(name);

        if (instance == null) {
            try {
                state = GsonUtils.GLOBAL_GSON.fromJson(FileUtil.read(modsJson.getPath()), State.class);
                instance = state.getInstance(name);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    public static String getWorkDir() {
        return workDir;
    }

    //Will only save the state if there is nothing currently happening
    public static void saveState() {
        Thread thread = new Thread() {
            public void run() {
                while (currentDownloadSlugs.size() > 0) {
                    synchronized (state) {
                        try {
                            state.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                GsonUtils.objectToJsonFile(workDir + "/mods.json", GsonUtils.GLOBAL_GSON.toJson(state));
                saveStateCalled = false;
            }
        };
        if (!saveStateCalled) {
            saveStateCalled = true;
            thread.start();
        }
    }

    public static boolean isDownloading(String slug) {
        return currentDownloadSlugs.contains(slug);
    }

    public static void createInstance(String name, String gameVersion, String loaderType) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                String loaderVersion;
                if (loaderType.equals("fabric")) {
                    loaderVersion = Fabric.getLatestLoaderVersion();
                    Fabric.downloadJson(gameVersion, loaderVersion);
                }

                String profileName = String.format("%s-%s-%s", loaderType + "-loader", loaderType, gameVersion);
                Instance instance = new Instance();
                instance.setName(name);
                instance.setGameVersion(gameVersion);
                instance.setLoaderVersion(profileName);
                state.addInstance(instance);
                saveState();
            }
        };
        thread.start();
    }

    public static void addMod(Instance instance, String platform, String slug, String gameVersion) {
        Thread thread = new Thread() {
            public void run() {
                currentDownloadSlugs.add(slug);

                File path;
                path = new File(workDir + "/instances/" + instance.getName());
                if (!path.exists()) path.mkdir();

                try {
                    ModData modData = null;
                    if (platform.equals("modrinth")) modData = Modrinth.getModData(slug, gameVersion);
                    if (modData == null) return;
                    modData.isActive = true;

                    //No duplicate mods allowed
                    for (ModData mod : instance.getMods()) {
                        if (mod.slug.equals(modData.slug)) return;
                    }
                    instance.addMod(modData);

                    DownloadUtils.downloadFile(modData.fileData.url, new File(path.getPath() + "/" + modData.fileData.filename));
                    currentDownloadSlugs.remove(slug);

                    GsonUtils.objectToJsonFile(workDir + "/mods.json", GsonUtils.GLOBAL_GSON.toJson(state));
                    synchronized (state) {
                        state.notifyAll();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public static void removeMod(String instanceName, String slug) {
        Instance instance = state.getInstance(instanceName);
        removeMod(instance, instance.getMod(slug));
    }

    public static void removeMod(Instance instance, ModData modData) {
        File modJar = new File(workDir + "/instances/" + instance.getName() + "/" + modData.fileData.filename);
        if (modJar.delete()) {
            instance.getMods().remove(modData);
            GsonUtils.objectToJsonFile(workDir + "/mods.json", GsonUtils.GLOBAL_GSON.toJson(state));
        }
    }

    //Returns a list of mods that need to be updated
    public static ArrayList<ModData> checkModsForUpdate(String instanceName) {
        ArrayList<ModData> mods = new ArrayList<>();
        try {
            Instance instance = getInstance(instanceName);
            if(instance.getMods() != null) {
                for (ModData mod : instance.getMods()) {
                    ModData modData = null;
                    if (mod.platform.equals("modrinth"))
                        modData = Modrinth.getModData(mod.slug, instance.getGameVersion());
                    if (modData != null && !mod.fileData.id.equals(modData.fileData.id) && !Objects.equals(modData.slug, "simple-voice-chat"))
                        mods.add(mod);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mods;
    }

    public static void updateMods(String instanceName, ArrayList<ModData> modsToUpdate) {
        Instance instance = state.getInstance(instanceName);
        for (ModData mod : modsToUpdate) {
            removeMod(instance, mod);
            if(instance.getGameVersion().equals("1.19.2")) {
                addMod(instance, mod.platform, mod.slug, "1.19");
            } else {
                addMod(instance, mod.platform, mod.slug, instance.getGameVersion());
            }
        }
    }

    public static void setModActive(String instanceName, String slug, boolean active) {
        Thread thread = new Thread() {
            public void run() {
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.O) return;

                Instance instance = state.getInstance(instanceName);
                ModData modData = instance.getMod(slug);
                if (modData == null) return;

                modData.isActive = active;
                String suffix = "";
                if (!active) suffix = ".disabled";

                File path = new File(workDir + "/instances/" + instanceName);
                for (File modJar : path.listFiles()) {
                    if (modJar.getName().replace(".disabled", "").equals(modData.fileData.filename)) {
                        try {
                            Path source = Paths.get(modJar.getPath());
                            Files.move(source, source.resolveSibling(modData.fileData.filename + suffix));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                saveState();
            }
        };
        thread.start();
    }

    public static ArrayList<ModData> listInstalledMods(String instanceName) {
        return (ArrayList<ModData>) getInstance(instanceName).getMods();
    }
}
