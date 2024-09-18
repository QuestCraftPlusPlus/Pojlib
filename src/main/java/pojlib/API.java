package pojlib;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import com.google.gson.JsonObject;

import pojlib.account.MinecraftAccount;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.Constants;
import pojlib.account.LoginHelper;

import java.io.IOException;
import java.util.Date;

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
    public static boolean finishedDownloading = true;
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
     * Add a mod to an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#(Activity, MinecraftInstances, String, String, boolean, String, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @param version Project version
     * @param url Mod download URL
     */
    public static void addExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String version, String url, String type) {
        InstanceHandler.addExtraProject(instances, instance, name, version, url, type);
    }

    /**
     * Check if an instance has a mod
     *
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String)}
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
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name project name
     * @return True if the project was deleted
     */
    public static boolean removeExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.removeExtraProject(instances, instance, name);
    }

    public static String[] getQCSupportedVersions() {
        return APIHandler.getQCSupportedVersions();
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
    public static boolean deleteInstance(MinecraftInstances instances, MinecraftInstances.Instance instance) {
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
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, boolean useDefaultMods, String minecraftVersion, String imageURL) throws IOException {

        if(ignoreInstanceName) {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, useDefaultMods, minecraftVersion, InstanceHandler.ModLoader.Fabric, imageURL);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, useDefaultMods, minecraftVersion, InstanceHandler.ModLoader.Fabric, imageURL);
        }
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
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, String imageURL, String mrpackFile) throws IOException {

        if(ignoreInstanceName) {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, InstanceHandler.ModLoader.Fabric, mrpackFile, imageURL);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, InstanceHandler.ModLoader.Fabric, mrpackFile, imageURL);
        }
    }

    /**
     * Update the mods for the selected instance
     *
     * @param instance The instance to update
     */
    public static void updateMods(MinecraftInstances instances, MinecraftInstances.Instance instance) {
        instance.updateMods(instances);
    }

    /**
     * Launch an instance
     *
     * @param activity Android activity object
     * @param account Account object
     * @param instance Instance object from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     */
    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        InstanceHandler.launchInstance(activity, account, instance);
    }

    /**
     * Logs the user out
     *
     * @param activity The base directory where minecraft should be setup
     * @return True if logout was successful
     */
    public static boolean logout(Activity activity) {
        return MinecraftAccount.logout(activity);
    }

    /**
     * Start the login process
     *
     * @param activity Android activity object
     */
    public static void login(Activity activity)
    {
        ConnectivityManager connManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());

        boolean hasWifi = true;

        if(capabilities != null) {
            hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }

        MinecraftAccount acc = MinecraftAccount.load(activity.getFilesDir() + "/accounts", null, null);
        if(acc != null && (acc.expiresIn > System.currentTimeMillis() || !hasWifi)) {
            currentAcc = acc;
            API.profileImage = MinecraftAccount.getSkinFaceUrl(API.currentAcc);
            API.profileName = API.currentAcc.username;
            return;
        } else if(acc != null && acc.expiresIn <= System.currentTimeMillis()) {
            currentAcc = LoginHelper.getNewToken(activity);
            if(currentAcc == null) {
                LoginHelper.beginLogin(activity);
            } else {
                API.profileImage = MinecraftAccount.getSkinFaceUrl(API.currentAcc);
                API.profileName = API.currentAcc.username;
            }
        }

        LoginHelper.beginLogin(activity);
    }
}
