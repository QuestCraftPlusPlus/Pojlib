package pojlib.account;

import static pojlib.account.Msa.checkMcProfile;

import android.app.Activity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.LoginHelper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.annotation.Nullable;

public class MinecraftAccount {

    public String accessToken;
    public String uuid;
    public String username;
    public long expiresIn;

    public final String userType = "msa";

    public static MinecraftAccount login(String gameDir, String[] response) throws IOException, JSONException {
        String mcToken = Msa.acquireXBLToken(response[0]);
        MinecraftAccount account = checkMcProfile(mcToken);
        account.expiresIn = Long.parseLong(response[1]);

        GsonUtils.objectToJsonFile(gameDir + "/account.json", account);
        return account;
    }

    public static boolean logout(String path) {
        File accountFile = new File(path + "/account.json");
        return accountFile.delete();
    }

    //Try this before using login - the account will have been saved to disk if previously logged in
    public static MinecraftAccount load(String path, @Nullable String newToken, @Nullable String expire) {
        MinecraftAccount acc;
        try {
            acc = new Gson().fromJson(new FileReader(path + "/account.json"), MinecraftAccount.class);
            if(newToken != null) {
                acc.accessToken = Msa.acquireXBLToken(newToken);
            }
            if(expire != null) {
                acc.expiresIn = Long.parseLong(expire);
            }
            GsonUtils.objectToJsonFile(path + "/account.json", acc);
            return acc;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    public static String getSkinFaceUrl(MinecraftAccount account) {
        return Constants.CRAFATAR_URL + "/avatars/" + account.uuid;
    }

}