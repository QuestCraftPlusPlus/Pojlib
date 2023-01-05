package pojlib.account;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import pojlib.util.Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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

    @NotNull
    static String read(InputStream is) throws IOException {
        StringBuilder out = new StringBuilder();
        int len;
        byte[] buf = new byte[512];
        while((len = is.read(buf))!=-1) {
            out.append(new String(buf, 0, len));
        }
        return out.toString();
    }

    public static MinecraftAccount acquireXBLToken(String accessToken) throws IOException, JSONException {
        URL url = new URL(Constants.XBL_AUTH_URL);

        Map<Object, Object> data = new HashMap<>();
        Map<Object, Object> properties = new HashMap<>();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", "d=" + accessToken);
        data.put("Properties",properties);
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");

        String req = ofJSONData(data);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        JSONObject jo = new JSONObject(read(conn.getInputStream()));
        if(!jo.isNull("Token")) {
            return acquireXsts(jo.getString("Token"));
        }

        File errorFile = new File(Constants.USER_HOME + "/errors.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(errorFile));
        writer.write(jo.toString());
        writer.flush();
        throw new RuntimeException();
    }

    private static MinecraftAccount acquireXsts(String xblToken) throws IOException, JSONException {
        URL url = new URL(Constants.XSTS_AUTH_URL);

        Map<Object, Object> data = new HashMap<>();
        Map<Object, Object> properties = new HashMap<>();
        properties.put("SandboxId", "RETAIL");
        properties.put("UserTokens",Collections.singleton(xblToken));
        data.put("Properties",properties);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");

        String req = ofJSONData(data);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try(OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        JSONObject jo = new JSONObject(read(conn.getInputStream()));

        if(!jo.isNull("Token")) {
            String uhs = jo.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
            return acquireMinecraftToken(uhs,jo.getString("Token"));
        }

        File errorFile = new File(Constants.USER_HOME + "/errors.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(errorFile));
        writer.write(jo.toString());
        writer.flush();
        throw new RuntimeException();
    }

    private static MinecraftAccount acquireMinecraftToken(String xblUhs, String xblXsts) throws IOException, JSONException {
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

        JSONObject jo = new JSONObject(read(conn.getInputStream()));

        if(!jo.isNull("access_token")) {
            checkMcStore(jo.getString("access_token"));
            MinecraftAccount account = checkMcProfile(jo.getString("access_token"));
            account.accessToken = jo.getString("access_token");
            return account;
        }

        File errorFile = new File(Constants.USER_HOME + "/errors.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(errorFile));
        writer.write(jo.toString());
        writer.flush();
        throw new RuntimeException();
    }

    private static void checkMcStore(String mcAccessToken) throws IOException, JSONException {
        URL url = new URL(Constants.MC_STORE_URL);

        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);
        conn.connect();

        String errStr = read(conn.getInputStream());
        if(errStr.contains("NOT_FOUND") && errStr.contains("The server has not found anything matching the request URI")) {
            File errorFile = new File(Constants.USER_HOME + "/errors.txt");
            BufferedWriter writer = new BufferedWriter(new FileWriter(errorFile));
            writer.write(errStr);
            writer.flush();
        }
    }

    private static MinecraftAccount checkMcProfile(String mcAccessToken) throws IOException, JSONException {
        URL url = new URL(Constants.MC_PROFILE_URL);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
        conn.setUseCaches(false);
        conn.connect();

        String s= read(conn.getInputStream());
        if (s.contains("NOT_FOUND") && s.contains("The server has not found anything matching the request URI")) {
            File errorFile = new File(Constants.USER_HOME + "/errors.txt");
            BufferedWriter writer = new BufferedWriter(new FileWriter(errorFile));
            writer.write(s);
            writer.flush();
        }
        JSONObject jsonObject = new JSONObject(s);
        String name = (String) jsonObject.get("name");
        String uuid = (String) jsonObject.get("id");
        String uuidDashes = uuid .replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
        );

        MinecraftAccount account = new MinecraftAccount();
        account.username = name;
        account.uuid = uuidDashes;
        return account;
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
}