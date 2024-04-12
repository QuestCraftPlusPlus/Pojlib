package pojlib.api;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import com.google.gson.JsonObject;

import pojlib.account.MinecraftAccount;
import pojlib.install.*;
import pojlib.instance.InstanceHandler;
import pojlib.instance.MinecraftInstances;
import pojlib.util.APIHandler;
import pojlib.util.LoginHelper;

import java.io.IOException;
import java.util.Date;

/**
 * This class is the only class used by the launcher to communicate and talk to pojlib. This keeps pojlib and launcher separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API_V1 {

    public static String msaMessage = "";
    public static String model = "Quest";
    private static boolean hasQueried = false;
    private static JsonObject initialResponse;
    public static boolean finishedDownloading = false;
    public static boolean ignoreInstanceName;
    public static boolean customRAMValue = false;
    public static double downloadStatus;
    public static String currentDownload;
    public static String profileImage;
    public static String profileName;
    public static String memoryValue = "1800";
    public static boolean developerMods;
    public static MinecraftAccount currentAcc;

    public static boolean advancedDebugger;


    /**
     * @return A list of every minecraft version
     */
    public static MinecraftMeta.MinecraftVersion[] getMinecraftVersions() {
        return MinecraftMeta.getVersions();
    }

    public static void addMod(MinecraftInstances instances, MinecraftInstances.Instance instance,
                              String home, String name, String version, String url) {
        InstanceHandler.addMod(instances, instance, home, name, version, url);
    }

    public static boolean hasMod(MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.hasMod(instance, name);
    }

    /**
     * @return if the operation succeeds
     */
    public static boolean removeMod(MinecraftInstances instances, MinecraftInstances.Instance instance,
                                    String home, String name) {
        return InstanceHandler.removeMod(instances, instance, home, name);
    }

    public static MinecraftMeta.MinecraftVersion[] getQCSupportedVersions() throws IOException {
        return APIHandler.getQCSupportedVersions();
    }

    public static String getQCSupportedVersionName(MinecraftMeta.MinecraftVersion version) {
        return APIHandler.getQCSupportedVersionName(version);
    }

    /**
     * Loads all instances from the filesystem.
     *
     * @param gameDir           .minecraft directory.
     * @return                  A minecraft instance object
     */
    public static MinecraftInstances loadAll(String gameDir) {
        return InstanceHandler.load(gameDir);
    }

    public static MinecraftInstances.Instance load(MinecraftInstances instances, String name) {
        return instances.load(name);
    }

    public static boolean deleteInstance(MinecraftInstances instances, MinecraftInstances.Instance instance, String gameDir) {
        return InstanceHandler.delete(instances, instance, gameDir);
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param home              The base directory where minecraft should be setup
     * @param minecraftVersion  The version of minecraft to install
     * @param modLoader         The mod loader to install
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, String instanceName, String home, boolean useDefaultMods,
                                                                MinecraftMeta.MinecraftVersion minecraftVersion, InstanceHandler.ModLoader modLoader, String modsFolderName) throws IOException {

        if(ignoreInstanceName) {
            return InstanceHandler.create(activity, instanceName, home, useDefaultMods, minecraftVersion, modLoader, modsFolderName);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return InstanceHandler.create(activity, instanceName, home, useDefaultMods, minecraftVersion, modLoader, modsFolderName);
        }
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        InstanceHandler.launchInstance(activity, account, instance);
    }

    /**
     * Logs the user out
     *
     * @param activity  The base directory where minecraft should be setup
     * @return      True if logout was successful
     */
    public static boolean logout(Activity activity) {
        return MinecraftAccount.logout(activity);
    }

    public static void login(Activity activity)
    {
        ConnectivityManager connManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());

        boolean hasWifi = false;

        if(capabilities != null) {
            hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }

        MinecraftAccount acc = MinecraftAccount.load(activity.getFilesDir() + "/accounts", null, null);
        if(acc != null && (acc.expiresIn > new Date().getTime() || !hasWifi)) {
            currentAcc = acc;
            API_V1.profileImage = MinecraftAccount.getSkinFaceUrl(API_V1.currentAcc);
            API_V1.profileName = API_V1.currentAcc.username;
            return;
        } else if(acc != null && acc.expiresIn <= new Date().getTime()) {
            currentAcc = LoginHelper.getNewToken(activity);
            if(currentAcc == null) {
                LoginHelper.beginLogin(activity);
            } else {
                API_V1.profileImage = MinecraftAccount.getSkinFaceUrl(API_V1.currentAcc);
                API_V1.profileName = API_V1.currentAcc.username;
            }
        }

        LoginHelper.beginLogin(activity);
    }
}
