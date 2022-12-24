package pojlib.account;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class MinecraftAccount {

    public String accessToken;
    public String msaRefreshToken;
    public String uuid;
    public String username;
    public int expiresIn;

    public final String userType = "msa";

    public static MinecraftAccount login(String gameDir, JsonObject response) throws IOException, JSONException {
        MinecraftAccount account = Msa.acquireXBLToken(response.get("access_token").getAsString());
        account.msaRefreshToken = response.get("refresh_token").getAsString();
        account.expiresIn = Instant.now().plusSeconds(response.get("expires_in").getAsInt()).getNano();

        GsonUtils.objectToJsonFile(gameDir + "/account.json", account);
        return account;
    }

    public static boolean logout(String path) {
        File accountFile = new File(path + "/account.json");
        return accountFile.delete();
    }

    //Try this before using login - the account will have been saved to disk if previously logged in
    public static MinecraftAccount load(String path, String client_id) {
        try {
            MinecraftAccount acc = new Gson().fromJson(new FileReader(path + "/account.json"), MinecraftAccount.class);
            if(Instant.now().getNano() >= acc.expiresIn) {
                try {
                    URLConnection connection2 = new URL("https://login.microsoftonline.com/consumers/oauth2/v2.0/token").openConnection();
                    connection2.setDoOutput(true);
                    connection2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    String query = String.format("client_id=%s&grant_type=%s&refresh_token=%s",
                            URLEncoder.encode(client_id, "UTF-8"),
                            URLEncoder.encode("refresh_token", "UTF-8"),
                            URLEncoder.encode(acc.msaRefreshToken, "UTF-8"));
                    try (OutputStream output = connection2.getOutputStream()) {
                        output.write(query.getBytes(StandardCharsets.UTF_8));
                    }

                    InputStream response = connection2.getInputStream();
                    JsonObject jsonObject = (JsonObject) JsonParser.parseReader(new InputStreamReader(response, StandardCharsets.UTF_8));

                    acc = Msa.acquireXBLToken(jsonObject.get("access_token").getAsString());
                    acc.msaRefreshToken = jsonObject.get("refresh_token").getAsString();
                    acc.expiresIn = Instant.now().plusSeconds(jsonObject.get("expires_in").getAsInt()).getNano();
                    GsonUtils.objectToJsonFile(path + "/account.json", acc);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
            return acc;
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