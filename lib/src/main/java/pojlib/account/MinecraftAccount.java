package pojlib.account;

import com.google.gson.Gson;
import org.json.JSONException;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class MinecraftAccount {

    public String accessToken;
    public String msaRefreshToken;
    public String uuid;
    public String username;

    public final String userType = "msa";

    public static MinecraftAccount login(String gameDir, String accessToken, String refreshToken) throws IOException, JSONException {
        MinecraftAccount account = Msa.acquireXBLToken(accessToken);
        account.msaRefreshToken = refreshToken;
        GsonUtils.objectToJsonFile(gameDir + "/account.json", account);
        return account;
    }

    public static boolean logout(String path) {
        File accountFile = new File(path + "/account.json");
        return accountFile.delete();
    }

    //Try this before using login - the account will have been saved to disk if previously logged in
    public static MinecraftAccount load(String path) {
        try {
            return new Gson().fromJson(new FileReader(path + "/account.json"), MinecraftAccount.class);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static String getSkinFaceUrl(MinecraftAccount account) {
        return Constants.CRAFATAR_URL + "/avatar/" + account.uuid;
    }

    public static String getSkinBodyUrl(MinecraftAccount account) {
        return Constants.CRAFATAR_URL + "/renders/body/" + account.uuid;
    }
}