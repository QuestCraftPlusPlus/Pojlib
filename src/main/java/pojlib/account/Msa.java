package pojlib.account;

import android.app.Activity;
import android.os.FileUtils;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pojlib.API;
import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.Logger;
import pojlib.util.MSAException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import javax.xml.transform.ErrorListener;


public class Msa {

    private final boolean mIsRefresh;
    private final String mAuthCode;
    private static final Map<Long, String> XSTS_ERRORS;
    static {
        XSTS_ERRORS = new ArrayMap<>();
        XSTS_ERRORS.put(2148916233L, "You don't seem to have an Xbox Live account. Please log in once on https://minecraft.net/ and try again.");
        XSTS_ERRORS.put(2148916235L, "Xbox Live is not available in your country.");
        XSTS_ERRORS.put(2148916236L, "An adult needs to verify your account.");
        XSTS_ERRORS.put(2148916237L, "An adult needs to verify your account.");
        XSTS_ERRORS.put(2148916238L, "Your account is a child account, and needs to be added into a Family in order to log in.");
    }

    /* Fields used to fill the account  */
    public String msRefreshToken;
    public static String mcName;
    public String mcToken;
    public static String mcUuid;
    public static boolean doesOwnGame;
    public long expiresAt;

    public Msa(boolean isRefresh, String authCode){
        mIsRefresh = isRefresh;
        mAuthCode = authCode;
    }

    /** Performs a full login, calling back listeners appropriately  */
    public MinecraftAccount performLogin(String xblToken) {
        try {
            String[] xsts = acquireXsts(xblToken);
            if(xsts == null) {
                return null;
            }
            String mcToken = acquireMinecraftToken(xsts[0], xsts[1]);
            fetchOwnedItems(mcToken);
            checkMcProfile(mcToken);

            MinecraftAccount acc = MinecraftAccount.load(mcName, null, null);
            if (acc == null) acc = new MinecraftAccount();
            if (doesOwnGame) {
                acc.accessToken = mcToken;
                acc.username = mcName;
                acc.uuid = mcUuid;
                acc.expiresIn = expiresAt;
            } else {
                Logger.getInstance().appendToLog("MicrosoftLogin | Unknown Error occurred.");
                throw new MSAException("MicrosoftLogin | Unknown Error occurred.", null);
            }

            return acc;
        } catch (Exception e) {
            Logger.getInstance().appendToLog("MicrosoftLogin | Exception thrown during authentication " + e);
            throw new MSAException("MicrosoftLogin | Exception thrown during authentication ", e);
        }
    }

    static String acquireXBLToken(String accessToken) throws IOException, JSONException {
        URL url = new URL(Constants.XBL_AUTH_URL);

        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", "d=" + accessToken);
        data.put("Properties", properties);
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");

        String req = data.toString();
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        setCommonProperties(conn, req);
        conn.connect();

        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(FileUtil.read(conn.getInputStream()));
            conn.disconnect();
            return jo.getString("Token");
        }else{
            throw getResponseThrowable(conn);
        }
    }

    /** @return [uhs, token]*/
    private String[] acquireXsts(String xblToken) throws IOException, JSONException {
        URL url = new URL(Constants.XSTS_AUTH_URL);

        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("SandboxId", "RETAIL");
        properties.put("UserTokens", new JSONArray(Collections.singleton(xblToken)));
        data.put("Properties", properties);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");

        String req = data.toString();
        // Logger.getInstance().appendToLog("MicrosoftLogin | " + req);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        setCommonProperties(conn, req);
        // Logger.getInstance().appendToLog("MicrosoftLogin | " + conn.getRequestMethod());
        conn.connect();

        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(FileUtil.read(conn.getInputStream()));
            String uhs = jo.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
            String token = jo.getString("Token");
            conn.disconnect();
            return new String[]{uhs, token};
        } else if(conn.getResponseCode() == 401) {
            String responseContents = FileUtil.read(conn.getErrorStream());
            JSONObject jo = new JSONObject(responseContents);
            long xerr = jo.optLong("XErr", -1);
            String locale_id = XSTS_ERRORS.get(xerr);
            if(locale_id != null) {
                Logger.getInstance().appendToLog(responseContents);
                throw new MSAException(responseContents, null);
            }
            // Logger.getInstance().appendToLog("Unknown error returned from Xbox Live\n" + responseContents);
            throw new MSAException("Unknown error returned from Xbox Live", null);
        } else{
            throw getResponseThrowable(conn);
        }
    }

    private String acquireMinecraftToken(String xblUhs, String xblXsts) throws IOException, JSONException {
        URL url = new URL(Constants.MC_LOGIN_URL);

        JSONObject data = new JSONObject();
        data.put("identityToken", "XBL3.0 x=" + xblUhs + ";" + xblXsts);

        String req = data.toString();
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        setCommonProperties(conn, req);
        conn.connect();

        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            expiresAt = System.currentTimeMillis() + 86400000;
            JSONObject jo = new JSONObject(FileUtil.read(conn.getInputStream()));
            conn.disconnect();
            mcToken = jo.getString("access_token");
            return jo.getString("access_token");
        }else{
            throw getResponseThrowable(conn);
        }
    }

    private void fetchOwnedItems(String mcAccessToken) throws IOException {
        URL url = new URL(Constants.MC_STORE_URL);

        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
        conn.setUseCaches(false);
        conn.connect();
        if(conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) {
            throw getResponseThrowable(conn);
        }
        // We don't need any data from this request, it just needs to happen in order for
        // the MS servers to work properly. The data from this is practically useless
        // as it does not indicate whether the user owns the game through Game Pass.
    }

    // Returns false for failure //
    public static boolean checkMcProfile(String mcAccessToken) throws IOException, JSONException {
        URL url = new URL(Constants.MC_PROFILE_URL);

        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
        conn.setUseCaches(false);
        conn.connect();

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            String s = FileUtil.read(conn.getInputStream());
            conn.disconnect();
            Logger.getInstance().appendToLog("MicrosoftLogin | profile:" + s);
            JSONObject jsonObject = new JSONObject(s);
            String name = (String) jsonObject.get("name");
            String uuid = (String) jsonObject.get("id");
            String uuidDashes = uuid.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
            );
            doesOwnGame = true;
            Logger.getInstance().appendToLog("MicrosoftLogin | UserName = " + name);
            Logger.getInstance().appendToLog("MicrosoftLogin | Uuid Minecraft = " + uuidDashes);
            mcName = name;
            mcUuid = uuidDashes;
            return true;
        } else {
            Logger.getInstance().appendToLog("MicrosoftLogin | It seems that this Microsoft Account does not own the game.");
            doesOwnGame = false;
            throw new MSAException("It seems like this account does not have a Minecraft profile. If you have Xbox Game Pass, please log in on https://minecraft.net/ and set it up.", null);
        }
    }

    /** Set common properties for the connection. Given that all requests are POST, interactivity is always enabled */
    private static void setCommonProperties(HttpURLConnection conn, String formData) {
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        try {
            conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
            conn.setRequestMethod("POST");
        }catch (ProtocolException e) {
            Logger.getInstance().appendToLog("MicrosoftAuth | " + e);
        }
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
    }

    /**
     * @param data A series a strings: key1, value1, key2, value2...
     * @return the data converted as a form string for a POST request
     */
    private static String convertToFormData(String... data) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for(int i=0; i<data.length; i+=2){
            if (builder.length() > 0) builder.append("&");
            builder.append(URLEncoder.encode(data[i], "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(data[i+1], "UTF-8"));
        }
        return builder.toString();
    }

    private static RuntimeException getResponseThrowable(HttpURLConnection conn) throws IOException {
        Logger.getInstance().appendToLog("MicrosoftLogin | Error code: " + conn.getResponseCode() + ": " + conn.getResponseMessage());
        if(conn.getResponseCode() == 429) {
            throw new MSAException("Too many requests, please try again later.", null);
        }
        throw new MSAException(conn.getResponseMessage(), null);
    }
}