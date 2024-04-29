package pojlib.util;

import static pojlib.UnityPlayerActivity.newFile;

import android.app.Activity;
import android.content.Context;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pojlib.api.API_V1;

public class FileUtil {

    public static String DIR_GAME_NEW;
    public static String DIR_HOME_VERSION;


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
        return read(Files.newInputStream(Paths.get(path)));
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
        Objects.requireNonNull(outPath.getParentFile()).mkdirs();

        BufferedOutputStream fos = new BufferedOutputStream(Files.newOutputStream(outPath.toPath()));
        fos.write(content, 0, content.length);
        fos.close();
    }

    public static void UnzipArchive(String archivePath, String archiveName, Activity activity) {
        try {
            File archive = new File(archivePath);
            FileUtils.writeByteArrayToFile(archive, FileUtil.loadFromAssetToByte(activity, archiveName));
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(archive.toPath()));
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(new File(Constants.USER_HOME + "/" + archiveName), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
