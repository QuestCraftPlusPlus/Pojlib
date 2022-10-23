package pojlib.unity.instance;

import pojlib.unity.install.*;

import java.io.File;
import java.io.IOException;

public class MinecraftInstance {

    public static void create(String instanceName, String home, MinecraftMeta.MinecraftVersion minecraftVersion, int modLoader) throws IOException {
        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
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

        String minecraftClasspath = Installer.downloadLibraries(minecraftVersionInfo, home);
        String modLoaderClasspath = Installer.downloadLibraries(modLoaderVersionInfo, home);
        String finalClasspath = minecraftClasspath + File.pathSeparator + modLoaderClasspath;

        Installer.downloadAssets(minecraftVersionInfo, home);
    }
}
