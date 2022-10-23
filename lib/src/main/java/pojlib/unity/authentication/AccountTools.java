package pojlib.unity.authentication;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pojlib.unity.util.DownloadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class AccountTools {

    public static String DIR_DATA; //Initialized later to get context
    public static final Gson GLOBAL_GSON = new GsonBuilder().setPrettyPrinting().create();
    public static String DIR_ACCOUNT_NEW;


    public static String read(InputStream is) throws IOException {
        StringBuilder out = new StringBuilder();
        int len;
        byte[] buf = new byte[512];
        while((len = is.read(buf))!=-1) {
            out.append(new String(buf, 0, len));
        }
        return out.toString();
    }

    public static void write(String path, String content) throws IOException {
        write(path, Arrays.toString(content.getBytes()));
    }

    public static void downloadFile(String urlInput, String nameOutput) throws IOException {
        File file = new File(nameOutput);
        DownloadUtils.downloadFile(urlInput, file);
    }

    public static String read(String path) throws IOException {
        return read(Files.newInputStream(Paths.get(path)));
    }

}
