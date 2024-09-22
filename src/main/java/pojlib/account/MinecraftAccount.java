package pojlib.account;

import android.app.Activity;

import com.google.gson.Gson;

import org.json.JSONException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.annotation.Nullable;

import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;
import pojlib.util.MSAException;

public class MinecraftAccount {
    public String accessToken;
    public String uuid;
    public String username;
    public long expiresOn;
    public final String userType = "msa";

    public static MinecraftAccount login(Activity activity, String gameDir, String msToken) throws MSAException, IOException, JSONException {
        Msa instance = new Msa(activity);
        MinecraftAccount account = instance.performLogin(msToken);

        GsonUtils.objectToJsonFile(gameDir + "/account.json", account);
        return account;
    }

    public static boolean logout(Activity activity) {
        File accountFile = new File(activity.getFilesDir() + "/accounts/account.json");
        File accountCache = new File(Constants.USER_HOME + "/cache_data");

        return accountFile.delete() && accountCache.delete();
    }

    //Try this before using login - the account will have been saved to disk if previously logged in
    public static MinecraftAccount load(String path) {
        return GsonUtils.jsonFileToObject(path + "/account.json", MinecraftAccount.class);
    }

    public static String getSkinFaceUrl(MinecraftAccount account) {
        try {
            return Constants.MINOTAR_URL + "/helm/" + account.uuid;
        } catch (NullPointerException e) {
            Logger.getInstance().appendToLog("Username likely not set! Please set your username at Minecraft.net and try again. | " + e);
            return null;
        }
    }
}
