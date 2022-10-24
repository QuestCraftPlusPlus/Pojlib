package pojlib.instance;

import com.google.gson.Gson;
import pojlib.install.*;
import pojlib.util.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MinecraftInstance {

    public String versionName;
    public String versionType;
    public String classpath;
    public String assetIndex;
    public String assetDir;

    //WIP!!!!!!
    //creates a new instance of a minecraft version, install game + mod loader, stores non login related launch info to json
    public static MinecraftInstance create(String instanceName, String path, MinecraftMeta.MinecraftVersion minecraftVersion, int modLoader) throws IOException {
        Logger.log(Logger.INFO, "Creating new instance: " + instanceName);

        MinecraftInstance instance = new MinecraftInstance();
        instance.versionName = minecraftVersion.id;

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        VersionInfo modLoaderVersionInfo = null;

        if (modLoader == 0) {
            FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestStableVersion();
            if (fabricVersion != null) modLoaderVersionInfo = FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
        }
        else if (modLoader == 1) {
            QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
            if (quiltVersion != null) modLoaderVersionInfo = QuiltMeta.getVersionInfo(quiltVersion, minecraftVersion);
        }
        else if (modLoader == 2) {
            throw new RuntimeException("Forge not yet implemented\nExiting...");
        }

        if (modLoaderVersionInfo == null) throw new RuntimeException("Error fetching mod loader data");

        String minecraftClasspath = Installer.downloadLibraries(minecraftVersionInfo, path);
        String modLoaderClasspath = Installer.downloadLibraries(modLoaderVersionInfo, path);
        instance.classpath = minecraftClasspath + File.pathSeparator + modLoaderClasspath;

        instance.assetIndex = minecraftVersionInfo.assetIndex.id;
        instance.assetDir = Installer.downloadAssets(minecraftVersionInfo, path);

        File file = new File(path + "/instances/" + instanceName);
        if (file.mkdirs()) new Gson().toJson(instance, new FileWriter(file.getAbsolutePath() + "/instance.json"));
        else Logger.log(Logger.ERROR, "Could not write instance to disk!");

        return instance;
    }
}
