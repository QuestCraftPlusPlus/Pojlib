package pojlib.util.download;

public class DownloadManager {
    private static long downloadedBytes;
    private static long totalBytes;
    
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
        if(totalBytes == 0) {
            return 1.0f;
        }

        return (float) downloadedBytes/totalBytes;
    }
}


