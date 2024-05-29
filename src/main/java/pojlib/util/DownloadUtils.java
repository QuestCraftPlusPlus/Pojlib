package pojlib.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import pojlib.api.API_V1;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

public class DownloadUtils {
    public static void downloadFile(String url, File out) throws IOException, RuntimeException 
    {
        Objects.requireNonNull(out.getParentFile()).mkdirs();
        File tempOut = File.createTempFile(out.getName(), ".part", out.getParentFile());

        try
        {
            int response = JREUtils.curlDownloadFile(url, tempOut.getAbsolutePath());
            API_V1.currentDownload = out.getName();
            API_V1.downloadStatus = 0;
            if(response == 0) // CURLE_OK 
            {
                Logger.getInstance().appendToLog("Successfully downloaded a file from \"" + url + "\" to \"" + out.getName() + "\".");
                tempOut.renameTo(out);
                if (tempOut.exists()) tempOut.delete();
            }
            else
            {
                if (tempOut.exists()) tempOut.delete();
                String curlError = JREUtils.curlResponseCodeString(response);
                throw new IOException("Failed to download a file from \"" + url + "\" to \"" + out.getName() + "\". cURL Error: " + curlError);
            }
        }
        catch(Exception e)
        {
            API_V1.currentDownload = null;
            API_V1.downloadStatus = 0;
            if (tempOut.exists()) tempOut.delete();
            Logger.getInstance().appendToLog(e.toString());
            throw e;
        }

        API_V1.currentDownload = null;
        API_V1.downloadStatus = 0;
    }
    public static boolean compareSHA1(File f, String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = Files.newInputStream(f.toPath())) {
                sha1_dst = new String(Hex.encodeHex(DigestUtils.sha1(is)));
            }
            if (sourceSHA != null) return sha1_dst.equalsIgnoreCase(sourceSHA);
            else return true; // fake match

        }catch (IOException e) {
            Logger.getInstance().appendToLog("Fake-matching a hash due to a read error: " + e);
            return true;
        }
    }
}
