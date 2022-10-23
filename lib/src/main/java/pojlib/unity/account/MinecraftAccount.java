package pojlib.unity.account;

import com.google.gson.Gson;
import org.json.JSONException;
import pojlib.unity.util.Constants;

import java.io.*;

public class MinecraftAccount {

    public String accessToken;
    public String msaRefreshToken;
    public String uuid;
    public String username;

    public static MinecraftAccount login(String path, String authCode) throws JSONException, IOException {
        MinecraftAccount account = Msa.getAccountFromAuthCode(false, authCode);

        new Gson().toJson(account, new FileWriter(path + "/account.json"));
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