package pojlib.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import pojlib.API;

import java.io.*;
import java.nio.file.Files;

import javax.annotation.Nullable;

public class DownloadUtils {

    private static DownloadManager downloadManagerObj;
    public static long downloadId = -1;

    public static DownloadManager downloadManager(Activity activity) {
        if (downloadManagerObj == null) {
            downloadManagerObj = (DownloadManager) activity.getSystemService(Activity.DOWNLOAD_SERVICE);
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            activity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        return downloadManagerObj;
    }

    public static long downloadFile(String url, File file, boolean awaitFile, Activity activity) throws IOException {
        API.finishedDownloading = false;

        if (API.hasConnection(activity)) {
            if (file.exists()) {
                file.delete();
            }
        } else {
            throw new RuntimeException("Cannot download from " + url + ", no internet connection");
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .addRequestHeader("User-Agent", "QuestCraft")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setDestinationUri(Uri.fromFile(file));

            downloadId = downloadManager(activity).enqueue(request);
            if (awaitFile) while(!API.finishedDownloading) {}
            return downloadId;
        } catch (Exception e) {
            Logger.getInstance().appendToLog("Error while downloading file: " + file + e);
            throw e;
        }
    }

    public static String getCurrentDownloadFilename(Activity activity) {
/*        DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Activity.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        Cursor cursor = downloadManager.query(query);

        String filename = null;

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    ContentResolver cr = activity.getContentResolver();
                    @SuppressLint("Range") int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

                    if (status == DownloadManager.STATUS_RUNNING) {
                        filename = cr.openFileDescriptor(cursor)
                        break;
                    }
                }
            } finally {
                cursor.close();
            }
        }*/

        return "Random silly goober name because stuff broky :3";
    }

    public static double getTotalProgress(Activity activity) {
        DownloadManager.Query query = new DownloadManager.Query();
        Cursor cursor = downloadManager(activity).query(query);

        long totalBytesDownloaded = 0;
        long totalBytes = 0;

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range") long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    @SuppressLint("Range") long bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    // Avoid adding to totalBytes if size is unknown (-1)
                    if (bytesTotal > 0) {
                        totalBytesDownloaded += bytesDownloaded;
                        totalBytes += bytesTotal;
                    }
                }
            } finally {
                cursor.close();
            }
        }

        if (totalBytes > 0) {
            return (double) totalBytesDownloaded / totalBytes;
        } else {
            return 0; // No active downloads or unknown total size
        }
    }

    private static final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (downloadId == id) {
                DownloadManager dm = downloadManagerObj;
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor c = dm.query(query);
                if (c.moveToFirst()) {
                    int colIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    API.finishedDownloading = DownloadManager.STATUS_RUNNING == c.getInt(colIndex);
                }
            }
        }
    };

    public static boolean compareSHA1(File f, @Nullable String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = Files.newInputStream(f.toPath())) {
                sha1_dst = new String(Hex.encodeHex(DigestUtils.sha1(is)));
            }
            if (sourceSHA != null) return sha1_dst.equalsIgnoreCase(sourceSHA);
            else return true; // No hash provided

        } catch (IOException e) {
            Logger.getInstance().appendToLog("Issue while comparing SHA1: " + e);
            return false;
        }
    }
}
