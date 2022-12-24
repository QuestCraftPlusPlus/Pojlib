package pojlib.util;

import android.content.Context;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {
    public static byte[] loadFromAssetToByte(Context ctx, String inFile) {
        byte[] buffer = null;

        try {
            InputStream stream = ctx.getAssets().open(inFile);

            int size = stream.available();
            buffer = new byte[size];
            stream.read(buffer);
            stream.close();
        } catch (IOException e) {
            // Handle exceptions here
            e.printStackTrace();
        }
        return buffer;
    }

    public static String read(String path) throws IOException {
        return read(new FileInputStream(path));
    }

    public static String read(InputStream is) throws IOException {
        StringBuilder out = new StringBuilder();
        int len;
        byte[] buf = new byte[512];
        while((len = is.read(buf))!=-1) {
            out.append(new String(buf, 0, len));
        }
        return out.toString();
    }

    public static void write(String path, byte[] content) throws IOException
    {
        File outPath = new File(path);
        outPath.getParentFile().mkdirs();
        //outPath.createNewFile();

        BufferedOutputStream fos = new BufferedOutputStream(Files.newOutputStream(Paths.get(path)));
        fos.write(content, 0, content.length);
        fos.close();
    }

}
