package pojlib.modmanager;

import com.google.gson.annotations.SerializedName;

public class ModData {
    @SerializedName("title")
    public String title;
    @SerializedName("slug")
    public String slug;
    @SerializedName("icon_url")
    public String iconUrl;

    public String platform;
    public String repo;
    public boolean isActive;
    public FileData fileData;

    public ModData() {
        isActive = false;
        fileData = new FileData();
    }

    //Only set when calling a getModFileData method
    public static class FileData {
        public String id;
        public String url;
        public String filename;
    }
}
