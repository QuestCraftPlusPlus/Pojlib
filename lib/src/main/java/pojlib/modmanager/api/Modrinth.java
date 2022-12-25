package pojlib.modmanager.api;

import android.os.Build;
import com.google.gson.annotations.SerializedName;

import pojlib.modmanager.ModData;
import pojlib.modmanager.ModManager;
import pojlib.util.APIHandler;
import pojlib.util.DownloadUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Modrinth {

    private static final APIHandler handler = new APIHandler("https://api.modrinth.com/v2");

    public static class Project {
        @SerializedName("title")
        public String title;
        @SerializedName("slug")
        public String slug;
        @SerializedName("icon_url")
        public String iconUrl;
        @SerializedName("body")
        public String body;
    }

    public static class Version {
        @SerializedName("id")
        public String id;
        @SerializedName("loaders")
        public List<String> loaders;
        @SerializedName("game_versions")
        public List<String> gameVersions;
        @SerializedName("files")
        public List<File> files;

        public static class File {
            @SerializedName("url")
            public String url;
            @SerializedName("filename")
            public String filename;
        }
    }

    public static class SearchResult {
        @SerializedName("hits")
        public List<ModData> hits;
    }

    public static ModData getModData(String slug, String gameVersion) throws IOException {
        Project project = handler.get("project/" + slug, Project.class);
        Version[] versions = handler.get("project/" + slug + "/version", Version[].class);

        if (project == null || versions == null) {
            return null;
        }

        for (Version modVersion : versions) {
            for (String loader : modVersion.loaders) {
                if (loader.equals("fabric")) {
                    for (String modGameVersion : modVersion.gameVersions) {
                        if (modGameVersion.equals(gameVersion)) {
                            Version.File file = modVersion.files.get(0);

                            ModData modData = new ModData();
                            modData.platform = "modrinth";
                            modData.title = project.title;
                            modData.slug = project.slug;
                            modData.iconUrl = project.iconUrl;
                            modData.fileData.id = modVersion.id;
                            modData.fileData.url = file.url;
                            modData.fileData.filename = file.filename;
                            return modData;
                        }
                    }
                }
            }
        }
        return null;
    }

/*    public static void addProjectsToRecycler(String version, int offset, String query) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                HashMap<String, Object> queries = new HashMap<>();
                queries.put("query", query);
                queries.put("offset", offset);
                queries.put("limit", 50);
                queries.put("facets", "[\"project_type:mod\"], [[\"categories:fabric\"], [\"versions:" + version + "\"]]");

                SearchResult result = handler.get("search", queries, SearchResult.class);
                if (result == null || Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                    return;
                }

                result.hits.removeIf(modData -> {
                    for (ModData coreMod : ModManager.listCoreMods(version)) {
                        if (coreMod.slug.equals(modData.slug)) return true;
                    }
                    return false;
                });

                ArrayList<ModData> installedMods = ModManager.listInstalledMods("fabric-loader-" + DownloadUtils.getModJsonFabricLoaderVersion() + "-" + version);
                for (ModData mod : result.hits) {
                    mod.platform = "modrinth";
                    for (ModData installedMod : installedMods) {
                        if (installedMod.isActive && installedMod.slug.equals(mod.slug)) {
                            mod.isActive = true;
                            break;
                        }
                    }
                }

                UiUitls.runOnUI(() -> {
                    adapter.addMods((ArrayList<ModData>) result.hits);
                    if (offset == 0 && result.hits.size() > 0) adapter.loadProjectPage(result.hits.get(0), null);
                });
            }
        };
        thread.start();
    }*/
}
