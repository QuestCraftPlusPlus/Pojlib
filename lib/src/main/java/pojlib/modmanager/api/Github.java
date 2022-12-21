package pojlib.modmanager.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import pojlib.modmanager.ModData;
import net.kdt.pojavlaunch.utils.APIUtils;
import net.kdt.pojavlaunch.utils.UiUitls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Github {

    private static final APIUtils.APIHandler handler = new APIUtils.APIHandler("https://api.github.com/repos");
    private static String[] repoList;

    public static class Release {
        @SerializedName("name")
        public String name;
        @SerializedName("id")
        public String id;
        @SerializedName("assets")
        public List<Asset> assets;
    }

    public static class Asset {
        @SerializedName("name")
        public String name;
        @SerializedName("browser_download_url")
        public String url;
    }

    public static void setRepoList(JsonArray repos) {
        ArrayList<String> r = new ArrayList<>();
        for (JsonElement repo : repos) {
            r.add(repo.getAsString());
        }
        repoList = r.toArray(new String[]{});
    }

    public static ModData getModData(String slug, String gameVersion) throws IOException {
        for (String repo : repoList) {
            String[] repoData = repo.split("/");
            Release[] releases = handler.get(String.format("%s/%s/releases", repoData[0], repoData[1]), Release[].class);

            if (releases == null) return null;

            for (Release release : releases) {
                if (release.name.split("-")[1].equals(gameVersion)) {
                    for (Asset asset : release.assets) {
                        if (asset.name.replace(".jar", "").equals(slug)) {
                            ModData modData = new ModData();

                            modData.platform = "github";
                            modData.repo = repo;
                            modData.title = slug;
                            modData.slug = slug;
                            modData.iconUrl = "https://avatars.githubusercontent.com/u/21025855?s=280&v=4"; //Fabric Icon
                            modData.fileData.id = release.id;
                            modData.fileData.url = asset.url;
                            modData.fileData.filename = asset.name;
                            return modData;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static void loadProjectPage(MarkdownView view, String repo) {
        view.loadMarkdown("", "file:///android_asset/ModDescription.css");
        UiUitls.runOnUI(() -> view.loadMarkdownFile("https://raw.githubusercontent.com/" + repo + "/master/README.md", "file:///android_asset/ModDescription.css"));
    }
}
