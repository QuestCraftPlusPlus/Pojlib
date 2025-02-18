package pojlib.util.download;

import java.util.ArrayList;
import java.util.List;

import pojlib.API;

public class DownloadManager {
    public static final List<DownloadManager> DOWNLOAD_STACK = new ArrayList<>();
    private final long byteCount;
    private long bytesToDownload;

    private DownloadManager(long byteCount) {
        this.byteCount = byteCount;
        this.bytesToDownload = byteCount;
    }

    public void updateProgress(String fileName, long bytesDownloaded) {
        API.currentDownload = fileName;
        bytesToDownload -= bytesDownloaded;

        API.downloadStatus = (bytesToDownload / byteCount) * 100;
    }

    public void fileDownloadComplete(long byteCount) {
        bytesToDownload -= byteCount;
        if (bytesToDownload <= 0) {
            DOWNLOAD_STACK.remove(this);
        }
    }

    public static DownloadManager addDownloadToStack(long byteCount) {
        DownloadManager manager = new DownloadManager(byteCount);
        DOWNLOAD_STACK.add(manager);
        return manager;
    }

    public static int currentDownloads() {
        return DOWNLOAD_STACK.size();
    }
}


