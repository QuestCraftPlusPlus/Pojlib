package pojlib.util;

import android.app.Activity;
import android.content.Context;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

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

    public static boolean matchingAssetFile(File sourceFile, byte[] assetFile) throws IOException {
        byte[] sf = Files.readAllBytes(sourceFile.toPath());
        return sf == assetFile;
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
        if(!outPath.exists()) {
            outPath.createNewFile();
        }

        BufferedOutputStream fos = new BufferedOutputStream(Files.newOutputStream(outPath.toPath()));
        fos.write(content, 0, content.length);
        fos.close();
    }

    public static void unzipArchive(String archivePath, String extractPath) {
        try {
            try(ZipFile zipFile = new ZipFile(archivePath)) {
                byte[] buf = new byte[1024];
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while(entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if(entry.isDirectory()) {
                        continue;
                    }

                    File newFile = newFile(new File(extractPath), entry);
                    newFile.getParentFile().mkdirs();

                    FileOutputStream fos = new FileOutputStream(newFile);
                    InputStream input = zipFile.getInputStream(entry);
                    int len;
                    while ((len = input.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                        fos.flush();
                    }
                    fos.close();
                }
            }
        } catch (IOException e) {
            Logger.getInstance().appendToLog(e.getMessage());
        }
    }

    public static void unzipArchiveFromAsset(Activity activity, String archiveName, String extractPath) {
        try {
            File zip = new File(extractPath, archiveName);
            FileUtils.writeByteArrayToFile(zip, FileUtil.loadFromAssetToByte(activity, archiveName));
            try(ZipFile zipFile = new ZipFile(zip)) {
                byte[] buf = new byte[1024];
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while(entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if(entry.isDirectory()) {
                        continue;
                    }

                    File newFile = newFile(new File(extractPath), entry);
                    newFile.getParentFile().mkdirs();

                    FileOutputStream fos = new FileOutputStream(newFile);
                    InputStream input = zipFile.getInputStream(entry);
                    int len;
                    while ((len = input.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                        fos.flush();
                    }
                    fos.close();
                }
            }
        } catch (IOException e) {
            Logger.getInstance().appendToLog(e.getMessage());
        }
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * @author PojavLauncherTeam
     * Same as ensureDirectorySilently(), but throws an IOException telling why the check failed.
     * @param targetFile the directory to check
     * @throws IOException when the checks fail
     */
    public static void ensureDirectory(File targetFile) throws IOException{
        if(targetFile.isFile()) throw new IOException("Target directory is a file");
        if(targetFile.exists()) {
            if(!targetFile.canWrite()) throw new IOException("Target directory is not writable");
        }else if(!targetFile.mkdirs()) throw new IOException("Unable to create target directory");
    }

    /**
     * @author PojavLauncherTeam
     * Same as ensureParentDirectorySilently(), but throws an IOException telling why the check failed.
     * @param targetFile the File whose parent should be checked
     * @throws IOException when the checks fail
     */
    public static void ensureParentDirectory(File targetFile) throws IOException{
        File parentFile = targetFile.getParentFile();
        if(parentFile == null) throw new IOException("targetFile does not have a parent");
        ensureDirectory(parentFile);
    }
}
