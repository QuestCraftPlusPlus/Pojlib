package pojlib.modmanager.api;

import android.os.Build;
import com.google.gson.annotations.SerializedName;

import net.kdt.pojavlaunch.Tools;
import pojlib.modmanager.ModData;
import pojlib.modmanager.ModManager;
import net.kdt.pojavlaunch.utils.APIUtils;
import net.kdt.pojavlaunch.utils.UiUitls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Curseforge {

    private static final APIUtils.APIHandler handler = new APIUtils.APIHandler("https://qcxr-modmanager-curseforge-api.herokuapp.com");

    public static class Project {
        @SerializedName("name")
        public String name;
        @SerializedName("id")
        public int id;
        @SerializedName("logo")
        public Logo logo;
        @SerializedName("latestFilesIndexes")
        public List<FileIndex> latestFilesIndexes;
    }

    public static class Logo {
        @SerializedName("thumbnailUrl")
        public String thumbnailUrl;
    }

    public static class FileIndex {
        @SerializedName("gameVersion")
        public String gameVersion;
        @SerializedName("fileId")
        public int fileId;
        @SerializedName("filename")
        public String filename;
        @SerializedName("modLoader")
        public int modLoader;
    }

    public static class ProjectResult {
        @SerializedName("data")
        public Project data;
    }

    public static class Description {
        @SerializedName("data")
        public String data;
    }

    public static class SearchResult {
        @SerializedName("data")
        public List<Project> data;
    }

    public static ModData getModData(String id, String gameVersion) throws IOException {
        ProjectResult projectResult = handler.get("getMod/" + id, ProjectResult.class);
        if (projectResult == null) return null;

        Project project = projectResult.data;
        for (FileIndex file : project.latestFilesIndexes) {
            if (file.modLoader == 4 && file.gameVersion.equals(gameVersion)) {
                ModData modData = new ModData();
                modData.platform = "curseforge";
                modData.title = project.name;
                modData.slug = String.valueOf(project.id);
                modData.iconUrl = project.logo.thumbnailUrl;
                modData.fileData.id = String.valueOf(file.fileId);
                modData.fileData.filename = file.filename;
                //Work around for curse restricting mods outside CurseForge platform
                modData.fileData.url = APIUtils.getCurseforgeJsonURL("https://qcxr-modmanager-curseforge-api.herokuapp.com" + "/getModDownloadURL/" + project.id + "/" + file.fileId);
                return modData;
            }
        }
        return null;
    }

    public static void addProjectsToRecycler(ModsFragment.ModAdapter adapter, String version, int offset, String query) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                HashMap<String, Object> queries = new HashMap<>();
                queries.put("gameId", 432);
                queries.put("gameVersion", version);
                queries.put("searchFilter", query);
                queries.put("modLoaderType", 4); //4 = fabric
                queries.put("index", offset);
                queries.put("pageSize", 50);
                queries.put("sortField", 6); //sort by most downloads
                queries.put("sortOrder", "desc");

                SearchResult searchResult = handler.get("searchMods", queries, SearchResult.class);
                if (searchResult == null || Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) return;

                ArrayList<ModData> mods = new ArrayList<>();
                for (Project project : searchResult.data) {
                    ModData modData = new ModData();
                    modData.platform = "curseforge";
                    modData.title = project.name;
                    modData.slug = String.valueOf(project.id);
                    if (project.logo != null) modData.iconUrl = project.logo.thumbnailUrl;
                    else modData.iconUrl = "";

                    for (ModData installedMod : ModManager.listInstalledMods("fabric-loader-" + Tools.getModJsonFabricLoaderVersion() + "-" + version)) {
                        if (installedMod.isActive && String.valueOf(project.id).equals(installedMod.slug)) {
                            modData.isActive = true;
                            break;
                        }
                    }
                    mods.add(modData);
                }

                mods.removeIf(modData -> {
                    for (ModData coreMod : ModManager.listCoreMods(version)) {
                        if (coreMod.slug.equals(modData.slug)) return true;
                    }
                    return false;
                });

                UiUitls.runOnUI(() -> {
                    adapter.addMods(mods);
                    if (offset == 0 && mods.size() > 0) adapter.loadProjectPage(mods.get(0), null);
                });
            }
        };
        thread.start();
    }

    public static void loadProjectPage(MarkdownView view, String id) {
        view.loadMarkdown("", "file:///android_asset/ModDescription.css");
        Thread thread = new Thread() {
            @Override
            public void run() {
                Description description = handler.get("getModDescription/" + id, Description.class);
                if (description != null) UiUitls.runOnUI(() -> view.loadMarkdown(description.data, "file:///android_asset/ModDescription.css"));
            }
        };
        thread.start();
    }
}
