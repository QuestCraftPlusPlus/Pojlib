package pojlib.modmanager;

import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class State {
    @SerializedName("fabric-loader-version")
    public String fabricLoaderVersion;
    @SerializedName("instances")
    private final List<Instance> instances = new ArrayList<>();
    @SerializedName("core_mods")
    private final HashMap<String, List<ModData>> coreMods = new HashMap<>();

    public List<Instance> getInstances() {
        return instances;
    }

    public Instance getInstance(String name) {
        for (Instance instance : instances) {
            if (instance.name.equalsIgnoreCase(name)) return instance;
        }
        return null;
    }

    public void addCoreMod(String version, ModData modData) {
        List<ModData> mods = coreMods.get(version);
        if (mods == null) mods = new ArrayList<>();
        mods.add(modData);
        coreMods.put(version, mods);
    }

    public List<ModData> getCoreMods(String version) {
        List<ModData> mods = coreMods.get(version);
        if (mods != null) return mods;
        return new ArrayList<>();
    }

    public void addInstance(Instance instance) {
        instances.add(instance);
    }

    public static class Instance {
        @SerializedName("name")
        private String name;
        @SerializedName("gameVersion")
        private String gameVersion;
        @SerializedName("LoaderVersion")
        private String LoaderVersion;
        @SerializedName("mods")
        private final List<ModData> mods = new ArrayList<>();


        public void setName(String name) {
            this.name = name;
        }

        public void setGameVersion(String gameVersion) {
            this.gameVersion = gameVersion;
        }

        public void setLoaderVersion(String fabricLoaderVersion) {
            this.LoaderVersion = fabricLoaderVersion;
        }

        public void addMod(ModData modData) {
            this.mods.add(modData);
        }

        public String getName() {
            return name;
        }

        public String getGameVersion() {
            return gameVersion;
        }

        public String getLoaderVersion() {
            return LoaderVersion;
        }

        public List<ModData> getMods() {
            return mods;
        }

        public ModData getMod(String slug) {
            for (ModData mod : mods) {
                if (mod.slug.equals(slug)) return mod;
            }
            return null;
        }
    }
}

