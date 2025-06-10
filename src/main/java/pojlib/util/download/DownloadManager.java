package pojlib.util.download;

public class DownloadManager {
    private static long downloadedBytes = 0;
    private static long totalBytes = 0;
    
    public static void addBytes(long add) {
        downloadedBytes += add;
    }
    
    public static void addTotalBytes(long add) {
        totalBytes += add;
    }

    public static void reset() {
        downloadedBytes = 0;
        totalBytes = 0;
    }

    public static boolean downloadsCompleted() {
        return downloadedBytes == totalBytes;
    }

    public static float getPercentComplete() {
        if(totalBytes == 0 || downloadedBytes > totalBytes) {
            return 100.0f;
        }

        return (float) downloadedBytes/totalBytes * 100;
    }
}


