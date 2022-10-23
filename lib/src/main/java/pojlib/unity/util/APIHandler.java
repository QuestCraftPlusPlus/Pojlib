package pojlib.unity.util;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

public class APIHandler {
    private final String baseUrl;

    public APIHandler(String url) {
        baseUrl = url;
    }

    public <T> T get(String endpoint, Class<T> tClass) {
        return getFullUrl(baseUrl + "/" + endpoint, tClass);
    }

    public <T> T get(String endpoint, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(baseUrl + "/" + endpoint, query, tClass);
    }

    //Make a get request and return the response as a raw string;
    public static String getRaw(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            InputStream inputStream = conn.getInputStream();
            String data = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            inputStream.close();
            conn.disconnect();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String parseQueries(HashMap<String, Object> query) {
        StringBuilder params = new StringBuilder("?");
        for (String param : query.keySet()) {
            Object value = query.get(param);
            params.append(param).append("=").append(value).append("&");
        }
        return params.substring(0, params.length() - 1);
    }

    public static  <T> T getFullUrl(String url, Class<T> tClass) {
        return new Gson().fromJson(getRaw(url), tClass);
    }

    public static  <T> T getFullUrl(String url, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(url + parseQueries(query), tClass);
    }
}