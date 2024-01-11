package pojlib.account;

import static pojlib.account.Msa.checkMcProfile;

import com.google.gson.Gson;

import org.json.JSONException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.annotation.Nullable;

import pojlib.util.Constants;
import pojlib.util.GsonUtils;

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
        //TODO: Log this to latestlog.txt for Support staff
        try {
            return Constants.MINOTAR_URL + "/helm/" + account.uuid;
        } catch (NullPointerException e) {
            System.out.println("Username not set! Please set your username at Minecraft.net and try again.");
            return null;
        }
    }
}
