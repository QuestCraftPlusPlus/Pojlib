package pojlib.unity.authentication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pojlib.unity.util.Constants;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class Msa {

    public String msRefreshToken;
    public String mcName;
    public String mcToken;
    public String mcUuid;
    public boolean doesOwnGame;

    public Msa(boolean isRefresh, String authCode) throws IOException, JSONException {
        acquireAccessToken(isRefresh, authCode);
    }

    public void acquireAccessToken(boolean isRefresh, String authcode) throws IOException, JSONException {
        URL url = new URL(Constants.OAUTH_TOKEN_URL);

        Map<Object, Object> data = new HashMap<>();
        /*Map.of(
         "client_id", "00000000402b5328",
         "code", authcode,
         "grant_type", "authorization_code",
         "redirect_uri", "https://login.live.com/oauth20_desktop.srf",
         "scope", "service::user.auth.xboxlive.com::MBI_SSL"
         );*/
        data.put("client_id", "00000000402b5328");
        data.put(isRefresh ? "refresh_token" : "code", authcode);
        data.put("grant_type", isRefresh ? "refresh_token" : "authorization_code");
        data.put("redirect_url", "https://login.live.com/oauth20_desktop.srf");
        data.put("scope", "service::user.auth.xboxlive.com::MBI_SSL");

        //да пошла yf[eq1 она ваша джава 11
        String req = ofFormData(data);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(req.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(AccountTools.read(conn.getInputStream()));
            msRefreshToken = jo.getString("refresh_token");
            acquireXBLToken(jo.getString("access_token"));
        }else{
            throwResponseError(conn);
        }

    }

    private void acquireXBLToken(String accessToken) throws IOException, JSONException {
        URL url = new URL(Constants.XBL_AUTH_URL);

        Map<Object, Object> data = new HashMap<>();
        Map<Object, Object> properties = new HashMap<>();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", accessToken);
        data.put("Properties",properties);
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");
        /*Map.of(

         "Properties", Map.of(
         "AuthMethod", "RPS",
         "SiteName", "user.auth.xboxlive.com",
         "RpsTicket", accessToken
         ),
         "RelyingParty", "http://auth.xboxlive.com",
         "TokenType", "JWT"
         );*/
        String req = ofJSONData(data);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(req.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(AccountTools.read(conn.getInputStream()));
            acquireXsts(jo.getString("Token"));
        }else{
            throwResponseError(conn);
        }
    }

    private void acquireXsts(String xblToken) throws IOException, JSONException {
        URL url = new URL(Constants.XSTS_AUTH_URL);

        Map<Object, Object> data = new HashMap<>();
        Map<Object, Object> properties = new HashMap<>();
        properties.put("SandboxId", "RETAIL");
        properties.put("UserTokens",Collections.singleton(xblToken));
        data.put("Properties",properties);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");
        /*Map<Object, Object> data = Map.of(
         "Properties", Map.of(
         "SandboxId", "RETAIL",
         "UserTokens", List.of(xblToken)
         ),
         "RelyingParty", "rp://api.minecraftservices.com/",
         "TokenType", "JWT"
         );
         */
        String req = ofJSONData(data);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(req.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(AccountTools.read(conn.getInputStream()));
            String uhs = jo.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
            acquireMinecraftToken(uhs,jo.getString("Token"));
        }else{
            throwResponseError(conn);
        }
    }

    private void acquireMinecraftToken(String xblUhs, String xblXsts) throws IOException, JSONException {
        URL url = new URL(Constants.MC_LOGIN_URL);

        Map<Object, Object> data = new HashMap<>();
        data.put("identityToken", "XBL3.0 x=" + xblUhs + ";" + xblXsts);
        String req = ofJSONData(data);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(req.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(AccountTools.read(conn.getInputStream()));
            mcToken = jo.getString("access_token");
            checkMcProfile(jo.getString("access_token"));
            checkMcStore(jo.getString("access_token"));

        }else{
            throwResponseError(conn);
        }
    }
    private void checkMcStore(String mcAccessToken) throws IOException, JSONException {
        URL url = new URL(Constants.MC_STORE_URL);

        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);
        conn.connect();
        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(AccountTools.read(conn.getInputStream()));
            JSONArray ja = jo.getJSONArray("items");
            for(int i = 0; i < ja.length(); i++) {
                String prod = ja.getJSONObject(i).getString("name");
            }
        }else{
            throwResponseError(conn);
        }
    }

    private void checkMcProfile(String mcAccessToken) throws IOException, JSONException {
        URL url = new URL(Constants.MC_PROFILE_URL);

        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
        conn.setUseCaches(false);
        conn.connect();

        if(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            String s= AccountTools.read(conn.getInputStream());
            JSONObject jsonObject = new JSONObject(s);
            String name = (String) jsonObject.get("name");
            String uuid = (String) jsonObject.get("id");
            String uuidDashes = uuid .replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
            );
            doesOwnGame = true;
            mcName=name;
            mcUuid=uuidDashes;
        }else{
            doesOwnGame = false;
            throwResponseError(conn);
        }
    }

    public static String ofJSONData(Map<Object, Object> data) {
        return new JSONObject(data).toString();
    }

    public static String ofFormData(Map<Object, Object> data) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            try {
                builder.append(URLEncoder.encode(entry.getKey().toString(), "UTF-8"));
                builder.append("=");
                builder.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                //Should not happen
            }
        }
        return builder.toString();
    }

    private static void throwResponseError(HttpURLConnection conn) throws IOException {
        String otherErrStr = "";
        String errStr = AccountTools.read(conn.getErrorStream());

        if (errStr.contains("NOT_FOUND") && errStr.contains("The server has not found anything matching the request URI")) {
            // TODO localize this
            otherErrStr = "It seems that this Microsoft Account does not own the game. Make sure that you have bought/migrated to your Microsoft account.";
        }

        throw new RuntimeException(otherErrStr + "\n\nMSA Error: " + conn.getResponseCode() + ": " + conn.getResponseMessage() + ", error stream:\n" + errStr);
    }
}


