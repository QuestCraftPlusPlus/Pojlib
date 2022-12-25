package pojlib.api;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Looper;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;
import org.json.JSONWriter;

import pojlib.account.MinecraftAccount;
import pojlib.android.R;
import pojlib.install.*;
import pojlib.instance.MinecraftInstance;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
    public static String profileImage;

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
    public static MinecraftAccount login(String home, String authCode, String refreshToken) throws IOException, JSONException {
        return MinecraftAccount.login(home, authCode, refreshToken);
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstance instance) {
        instance.launchInstance(activity, account);
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

    public static MinecraftAccount login(String client_id)
    {
        MinecraftAccount acc = MinecraftAccount.load(MinecraftInstance.context.getExternalFilesDir(null).getAbsolutePath() + "/.minecraft");
        if(acc != null) {
            return acc;
        }
        try {
            // Stage 1
            if(!hasQueried) {
                URLConnection connection = new URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode").openConnection();
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String query = String.format("client_id=%s&scope=%s",
                        URLEncoder.encode(client_id, "UTF-8"),
                        URLEncoder.encode("XboxLive.signin Xboxlive.offline_access", "UTF-8"));
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(query.getBytes(StandardCharsets.UTF_8));
                }

                InputStream response = connection.getInputStream();

                initialResponse = (JsonObject) JsonParser.parseReader(new InputStreamReader(response, StandardCharsets.UTF_8));

                msaMessage = initialResponse.get("message").getAsString();
            }

            if(hasQueried) {
                URLConnection connection2 = new URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/token").openConnection();
                connection2.setDoOutput(true);
                connection2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String query2 = String.format("client_id=%s&grant_type=%s&device_code=%s",
                        URLEncoder.encode(client_id, "UTF-8"),
                        URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", "UTF-8"),
                        URLEncoder.encode(initialResponse.get("device_code").getAsString(), "UTF-8"));
                try (OutputStream output = connection2.getOutputStream()) {
                    output.write(query2.getBytes(StandardCharsets.UTF_8));
                }

                InputStream response2 = connection2.getInputStream();

                JsonObject jsonObject2 = (JsonObject) JsonParser.parseReader(new InputStreamReader(response2, StandardCharsets.UTF_8));

                // Finally, log in
                return MinecraftAccount.login(Constants.USER_HOME + "/.minecraft", jsonObject2.get("access_token").getAsString(), jsonObject2.get("refresh_token").getAsString());
            }
            hasQueried = true;
            profileImage = MinecraftAccount.getSkinFaceUrl(acc).toString();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}
