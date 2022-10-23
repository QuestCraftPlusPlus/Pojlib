package pojlib.unity.api;


import pojlib.unity.install.*;

import java.io.IOException;

/**
 * This class is the only class used by unity to communicate and talk to pojlib. This keeps pojlib and unity separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API_V1 {

    /**
     * Returns a list of every minecraft version
     */
    public static MinecraftMeta.MinecraftVersion[] getMinecraftVersions() {
        return MinecraftMeta.getVersions();
    }

    /**
     * A collection of mod loader types
     */
    public enum ModLoader {
        Fabric,
        Quilt,
        Forge
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param instanceName The name of the instance being created - can be anything, used for identification
     * @param home The base directory where minecraft should be setup
     * @param minecraftVersion The version of minecraft to install
     * @param modLoader The type of mod loader to install
     *
     * @throws IOException Throws if download of library or asset fails
     */
    public static void createNewInstance(String instanceName, String home, MinecraftMeta.MinecraftVersion minecraftVersion, ModLoader modLoader) throws IOException {
        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        VersionInfo modLoaderVersionInfo;

        switch (modLoader) {
            case Fabric: {
                FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestStableVersion();
                if (fabricVersion != null) modLoaderVersionInfo = FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
            }
            case Quilt: {
                QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
                if (quiltVersion != null) modLoaderVersionInfo = QuiltMeta.getVersionInfo(quiltVersion, minecraftVersion);
            }
            case Forge: {
                System.out.println("Forge not yet implemented\nExiting...");
                return;
            }
        }

        Installer.downloadLibraries(minecraftVersionInfo, home);
        Installer.downloadLibraries(minecraftVersionInfo, home);
        Installer.downloadAssets(minecraftVersionInfo, home);
    }
}
