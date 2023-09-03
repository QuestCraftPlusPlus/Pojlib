package pojlib.api;

import android.app.Activity;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;

import pojlib.UnityPlayerActivity;
import pojlib.account.MinecraftAccount;
import pojlib.install.*;
import pojlib.instance.MinecraftInstance;
import pojlib.util.APIHandler;
import pojlib.util.Constants;
import pojlib.util.LoginHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * This class is the only class used by the launcher to communicate and talk to pojlib. This keeps pojlib and launcher separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API_V1 {

    public static String msaMessage = "";
    private static boolean hasQueried = false;
    private static JsonObject initialResponse;
    public static boolean finishedDownloading = false;
    public static boolean ignoreInstanceName;
    public static boolean customRAMValue = false;
    public static double downloadStatus;
    public static String currentDownload;
    public static String profileImage;
    public static String profileName;
    public static String memoryValue = "3072";
    public static boolean developerMods;
    public static MinecraftAccount currentAcc;
    public static boolean advancedDebugger;


    /**
     * @return A list of every minecraft version
     */
    public static MinecraftMeta.MinecraftVersion[] getMinecraftVersions() {
        return MinecraftMeta.getVersions();
    }

    public static void addCustomMod(MinecraftInstance instance, String name, String version, String url) {
        instance.addCustomMod(name, version, url);
    }

    public static boolean hasMod(MinecraftInstance instance, String name) {
        return instance.hasCustomMod(name);
    }

    /**
     * @return if the operation succeeds
     */
    public static boolean removeMod(MinecraftInstance instance, String name) {
        return instance.removeMod(name);
    }
    public static MinecraftMeta.MinecraftVersion[] getQCSupportedVersions() {
        return APIHandler.getQCSupportedVersions();
    }

    public static String getQCSupportedVersionName(MinecraftMeta.MinecraftVersion version) {
        return APIHandler.getQCSupportedVersionName(version);
    }

    /**
     * A collection of loader types
     */
    public enum ModLoader {
        Vanilla(0),
        Fabric(1),
        Quilt(2);

        public final int index;

        ModLoader(int i) {
            this.index = i;
        }
    }

    /**
     * Loads an instance from the filesystem.
     *
     * @param instanceName      The instance being loaded
     * @param gameDir           .minecraft directory.
     * @return                  A minecraft instance object
     */
    public static MinecraftInstance load(String instanceName, String gameDir) {
        return MinecraftInstance.load(instanceName, gameDir);
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param home              The base directory where minecraft should be setup
     * @param minecraftVersion  The version of minecraft to install
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstance createNewInstance(Activity activity, String instanceName, String home, MinecraftMeta.MinecraftVersion minecraftVersion) throws IOException {

        if(ignoreInstanceName) {
            return MinecraftInstance.create(activity, instanceName, home, minecraftVersion);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return MinecraftInstance.create(activity, instanceName, home, minecraftVersion);
        }
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstance instance) {
        instance.launchInstance(activity, account);
    }

    /**
     * Logs the user out
     *
     * @param home  The base directory where minecraft should be setup
     * @return      True if logout was successful
     */
    public static boolean logout(String home) {
        return MinecraftAccount.logout(home);
    }

    public static void login(Activity activity)
    {
        MinecraftAccount acc = MinecraftAccount.load(activity.getFilesDir() + "/accounts", null, null);
        if(acc != null && acc.expiresIn > new Date().getTime()) {
            currentAcc = acc;
            API_V1.profileImage = MinecraftAccount.getSkinFaceUrl(API_V1.currentAcc);
            API_V1.profileName = API_V1.currentAcc.username;
            return;
        } else if(acc != null && acc.expiresIn <= new Date().getTime()) {
            currentAcc = LoginHelper.getNewToken(activity);
            API_V1.profileImage = MinecraftAccount.getSkinFaceUrl(API_V1.currentAcc);
            API_V1.profileName = API_V1.currentAcc.username;
        }
        LoginHelper.beginLogin(activity);
    }
}
