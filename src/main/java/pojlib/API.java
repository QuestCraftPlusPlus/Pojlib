package pojlib;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import org.lwjgl.glfw.CallbackBridge;

import pojlib.account.MinecraftAccount;
import pojlib.util.FileUtil;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.Constants;
import pojlib.account.LoginHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * This class is the only class used by the launcher to communicate and talk to pojlib. This keeps pojlib and launcher separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API {

    public static String msaMessage = "";
    public static String model = "Quest";
    private static boolean hasQueried = false;
    private static JsonObject initialResponse;
    public static boolean ignoreInstanceName;
    public static boolean customRAMValue = false;
    public static String profileImage;
    public static String profileName;
    public static String profileUUID;
    public static String memoryValue = "1800";
    public static boolean developerMods;
    public static MinecraftAccount currentAcc;
    public static boolean isDemoMode;
    public static MinecraftInstances.Instance currentInstance;
    private static boolean hasWifi;
    public static boolean advancedDebugger;
    public static boolean gameReady = false;


    /**
     * Add a mod to an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#(Activity, MinecraftInstances, String, String, boolean, String, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @param version Project version
     * @param url Mod download URL
     */
    public static void addExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String fileName, String version, String url, String type) {
        InstanceHandler.addExtraProject(instances, instance, name, fileName, version, url, type);
    }

    public static boolean isDownloadsCompleted() {
        return DownloadManager.downloadsCompleted();
    }

    public static float getDownloadPercentage() {
        return DownloadManager.getPercentComplete();
    }

    public static boolean fixDataPermissions() {
        try {
            FileUtil.fixDirectoryPermissions(new File(Constants.USER_HOME + "/instances"));
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Couldn't fix directory permissions! | " + e);
            return false;
        }
        return true;
    }

    /**
     * Check if an instance has a mod
     *
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @return True if the project is already in the instance
     */
    public static boolean hasExtraProject(MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.hasExtraProject(instance, name);
    }

    /**
     * Remove a mod from an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name project name
     * @return True if the project was deleted
     */
    public static boolean removeExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.removeExtraProject(instances, instance, name);
    }

    public static String[] getQCSupportedVersions(Activity ctx) {
        return APIHandler.getQCSupportedVersions(ctx);
    }

    /**
     * Loads all instances from the filesystem.
     *
     * @return                  A minecraft instance object
     */
    public static MinecraftInstances loadAll() throws IOException {
        return InstanceHandler.load(Constants.USER_HOME);
    }

    /**
     * Load a specific instance by name
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param name Name of the instance
     * @return The instance, or null if an instance with name does not exist
     */
    public static MinecraftInstances.Instance load(MinecraftInstances instances, String name) {
        return instances.load(name);
    }

    /**
     * Delete an instance
     * NOTE: Only deletes the instance, not the correlated mods for said instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Instance object
     * @return True if it deletes successfully, false otherwise.
     */
    public static boolean deleteInstance(MinecraftInstances instances, MinecraftInstances.Instance instance) throws IOException {
        return InstanceHandler.delete(instances, instance);
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param useDefaultMods    Use QC's default mods for the version (Core mods are automatically included)
     * @param minecraftVersion  The version of minecraft to install
     * @param imageURL          Modpack image url, nullable
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, boolean useDefaultMods, String minecraftVersion, String modLoader, String imageURL) throws IOException {
        return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, useDefaultMods, minecraftVersion, modLoader, imageURL, null);
    }

    /**
     * Creates a new game instance from a mrpack file.
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param imageURL          Modpack image url, nullable
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, String imageURL, String modLoader, String mrpackFile) throws IOException {
        if(ignoreInstanceName) {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, modLoader, mrpackFile, imageURL);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, modLoader, mrpackFile, imageURL);
        }
    }

    /**
     * Update the mods for the selected instance
     *
     * @param instance The instance to update
     */
    public static boolean prelaunch(Activity activity, MinecraftInstances instances, MinecraftInstances.Instance instance) {
        gameReady = false;
        if (hasConnection(activity)) {
            instance.updateMods(instances);
            try {
                if(!JREUtils.prelaunchCheck(activity, instance)) {
                    Logger.getInstance().appendToLog("JVM is not properly installed despite retry!");
                    return false;
                }
            } catch (Throwable e) {
                Logger.getInstance().appendToLog("WARN! Instance launch failed!" + e);
                return false;
            }
        } else {
            Logger.getInstance().appendToLog("Skipping most prelaunch checks due to no WiFi connection.");
            if(!JREUtils.initJavaRuntime()) {
                Logger.getInstance().appendToLog("JVM is not properly installed! Please connect to WiFi to install it!");
                return false;
            }
        }
        DownloadManager.reset();

        MinecraftInstances.CheckVivecraftConfig(instance);
        return true;
    }

    /**
     * Launch an instance
     *
     * @param activity Android activity object
     * @param account Account object
     * @param instance Instance object from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     */
    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        InstanceHandler.launchInstance(activity, account, instance);
    }

    /**
     * Kill the current instance
     *
     * @param activity Android activity object
     */
    public static void restartLauncher(Activity activity) {
        Logger.getInstance().appendToLog("QuestCraft: Restarting launcher...");
        CallbackBridge.restartUnitySession(activity);
    }

    /**
     * Removes the user account
     *
     * @param activity The base directory where minecraft should be setup
     * @param uuid The uuid of the profile to remove
     * @return True if removal was successful
     */
    public static boolean removeAccount(Activity activity, String uuid) {
        if(currentAcc.uuid.equals(uuid)) {
            currentAcc = null;
        }
        return MinecraftAccount.removeAccount(activity, uuid);
    }

    /**
     * Start the login process
     *
     * @param activity Android activity object
     */
    public static void login(Activity activity, @Nullable String accountUUID)
    {
        if(accountUUID == null) {
            currentAcc = null;
            LoginHelper.login(activity);
            return;
        }

        MinecraftAccount acc = MinecraftAccount.load(activity.getFilesDir() + "/accounts", accountUUID);
        if(acc != null && (acc.expiresOn >= System.currentTimeMillis() || !hasConnection(activity) || acc.isDemoMode)) {
            API.profileImage = MinecraftAccount.getSkinFaceUrl(acc);
            API.profileName = acc.username;
            API.profileUUID = acc.uuid;
            API.isDemoMode = acc.isDemoMode;
            currentAcc = acc;
            return;
        } else if(acc != null && acc.expiresOn < System.currentTimeMillis()) {
            MinecraftAccount refreshed = LoginHelper.refreshAccount(activity, accountUUID);
            if(refreshed != null) {
                API.profileImage = MinecraftAccount.getSkinFaceUrl(refreshed);
                API.profileName = refreshed.username;
                API.profileUUID = refreshed.uuid;
                API.isDemoMode = refreshed.isDemoMode;
                currentAcc = refreshed;
                return;
            }
        }

        LoginHelper.login(activity);
    }

    /**
     * Check if the device has a valid wifi connection
     *
     * @param activity activity object
     * @return true if the device has a valid wifi connection
     */
    public static boolean hasConnection(Context activity) {
        boolean hasNetwork = false;
        ConnectivityManager connManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());

        if(capabilities != null) {
            hasNetwork = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }

        if (hasNetwork) {
            try {
                URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.connect();
                conn.disconnect();
                hasWifi = true;
                return true;
            } catch (Exception e) {
                Logger.getInstance().appendToLog("WARN! Unable to reach Microsoft servers!");
                hasWifi = false;
                return false;
            }
        } else {
            Logger.getInstance().appendToLog("WARN! Device has no wifi connection!");
            hasWifi = false;
            return false;
        }
    }

    public static void unzipSavesFromFolder(MinecraftInstances.Instance instance, String path) {
        try {
            Files.walkFileTree(Paths.get(path), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    installSave(instance, file);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });

            fixDataPermissions();
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to bulk install saves from path: " + path);
        }
    }

    public static void zipSavesFromInstance(MinecraftInstances.Instance instance, String path) throws IOException {
        FileOutputStream file = new FileOutputStream(path + "/out.zip");
        try(ZipOutputStream out = new ZipOutputStream(file)) {
            Files.walkFileTree(Paths.get(instance.gameDir + "/saves"), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    out.putNextEntry(new ZipEntry(dir.toString()
                            .replace(instance.gameDir + "/saves", "") + "/"));
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try(FileInputStream reader = new FileInputStream(file.toFile())) {
                        ZipEntry entry = new ZipEntry(file.toString()
                                .replace(instance.gameDir + "/saves", ""));
                        out.putNextEntry(entry);
                        int i = reader.read();
                        while(i > 0) {
                            out.write(i);
                            i = reader.read();
                        }
                        out.flush();
                    }
                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
            out.finish();
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to bulk export saves from path: " + path);
        }
        file.close();
    }

    public static void installSave(MinecraftInstances.Instance instance, Path file) {
        try {
            Path outPath = Paths.get(instance.gameDir, "saves");

            Files.createDirectories(outPath);
            FileUtil.unzipArchive(file.toString(), outPath.toString());
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to install save by file" + e);
        }
    }

    public static void mirrorNativesInFolder(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance, String path) {
        try {
            Files.createDirectories(activity.getDataDir().toPath().resolve(instance.instanceName));
            removeNatives(activity, instances, instance);

            Files.walkFileTree(Paths.get(path), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    installNativeFromExisting(activity, instances, instance, file.toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to bulk install natives from path: " + path);
        }
    }

    public static void installNativeFromExisting(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance, String nativePath) {
        try {
            Files.createDirectories(activity.getDataDir().toPath().resolve(instance.instanceName));
            String[] splitPath = nativePath.split("/");
            String name = splitPath[splitPath.length - 1];
            Path out = activity.getDataDir().toPath().resolve(instance.instanceName).resolve(name);

            Files.copy(Paths.get(nativePath), out);
            if(instance.extraNatives == null || instance.extraNatives.isEmpty()) {
                instance.extraNatives = out.toString();
            } else {
                instance.extraNatives += File.pathSeparator + out.toString();
            }

            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to install native from path: " + nativePath);
        }
    }

    public static void installNative(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance, String downloadPath, String downloadName) {
        try {
            Files.createDirectories(activity.getDataDir().toPath().resolve(instance.instanceName));
            Path out = activity.getDataDir().toPath().resolve(instance.instanceName).resolve(downloadName);

            DownloadUtils.downloadFile(downloadPath, out.toFile());
            if(instance.extraNatives == null || instance.extraNatives.isEmpty()) {
                instance.extraNatives = out.toString();
            } else {
                instance.extraNatives += File.pathSeparator + out;
            }

            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to install native from url: " + downloadPath);
        }
    }

    public static void removeNatives(Context activity, MinecraftInstances instances, MinecraftInstances.Instance instance) {
        try {
            Path out = activity.getDataDir().toPath().resolve(instance.instanceName);
            Files.walkFileTree(out, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            instance.extraNatives = "";

            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("ERROR! Failed to delete natives by folder");
        }
    }
}
