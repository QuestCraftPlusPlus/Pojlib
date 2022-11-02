package pojlib.api;

import android.app.Activity;

import com.google.gson.JsonObject;
import org.json.JSONException;
import pojlib.account.MicrosoftLoginGUIActivity;
import pojlib.account.MinecraftAccount;
import pojlib.install.*;
import pojlib.instance.MinecraftInstance;
import pojlib.util.GsonUtils;

import java.io.IOException;

/**
 * This class is the only class used by the launcher to communicate and talk to pojlib. This keeps pojlib and launcher separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API_V1 {

    /**
     * @return A list of every minecraft version
     */
    public static MinecraftMeta.MinecraftVersion[] getMinecraftVersions() {
        return MinecraftMeta.getVersions();
    }

    /**
     * A collection of loader types
     */
    public enum ModLoader {
        Vanilla(0),
        Fabric(1),
        Quilt(2),
        Forge(3);

        public final int index;

        ModLoader(int i) {
            this.index = i;
        }
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param home              The base directory where minecraft should be setup
     * @param minecraftVersion  The version of minecraft to install
     * @param modLoader         The type of mod loader to install
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstance createNewInstance(Activity activity, String instanceName, String home, MinecraftMeta.MinecraftVersion minecraftVersion, ModLoader modLoader) throws IOException {
        return MinecraftInstance.create(activity, instanceName, home, minecraftVersion, modLoader.index);
    }

    /**
     * Logs the user in and keeps them logged in unless they log out
     *
     * @param home      The base directory where minecraft should be setup
     * @param authCode  The token received from the microsoft login window
     * @return          A minecraft account object
     */
    public static MinecraftAccount login(String home, String authCode) throws IOException, JSONException {
        return MinecraftAccount.login(home, authCode);
    }

    /**
     * Fetches the account data from disk if the user has logged in before.
     *
     * @param home  The base directory where minecraft should be setup
     * @return      A minecraft account object, null if no account found
     */
    public static MinecraftAccount fetchSavedLogin(String home) {
        return MinecraftAccount.load(home);
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

//    public static void activity(Activity activity)
//    {
//        System.out.println("API_V1 Called");
//        MicrosoftLoginGUIActivity mslActivity = new MicrosoftLoginGUIActivity();
//        mslActivity.ActivitySwitch(activity);
//    }

}
