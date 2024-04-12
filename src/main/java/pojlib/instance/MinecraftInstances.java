package pojlib.instance;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;
import pojlib.util.DownloadUtils;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;
import pojlib.util.ModInfo;
import pojlib.util.ModsJson;

public class MinecraftInstances {
    public ArrayList<Instance> instances;

    public Instance load(String name) {
        for(Instance instance : instances) {
            if(instance.instanceName.equals(name)) {
                return instance;
            }
        }

        return null;
    }

    public static class Instance {
        public String instanceName;
        public String versionName;
        public String modsDirName;
        public String versionType;
        public String classpath;
        public String gameDir;
        public String assetIndex;
        public String assetsDir;
        public String mainClass;
        public ArrayList<ModInfo> mods;
        public boolean defaultMods;

        public List<String> generateLaunchArgs(MinecraftAccount account) {
            String[] mcArgs = {"--username", account.username, "--version", versionName, "--gameDir", gameDir,
                    "--assetsDir", assetsDir, "--assetIndex", assetIndex, "--uuid", account.uuid.replaceAll("-", ""),
                    "--accessToken", account.accessToken, "--userType", account.userType, "--versionType", versionType};

            List<String> allArgs = new ArrayList<>(Arrays.asList("-cp", classpath));
            allArgs.add(mainClass);
            allArgs.addAll(Arrays.asList(mcArgs));
            return allArgs;
        }

        private ModsJson parseModsJson(String gameDir) throws Exception {
            File mods = new File(gameDir + "mods.json");
            if(API_V1.developerMods) {
                DownloadUtils.downloadFile(InstanceHandler.DEV_MODS, mods);
            } else {
                DownloadUtils.downloadFile(InstanceHandler.MODS, mods);
            }

            return GsonUtils.jsonFileToObject(mods.getAbsolutePath(), ModsJson.class);
        }

        public void updateMods(String gameDir) {
            API_V1.finishedDownloading = false;
            try {
                ModsJson modsJson = parseModsJson(gameDir);

                ModsJson.Version version = null;
                for(ModsJson.Version info : modsJson.versions) {
                    if(!info.name.equals(versionName)) {
                        continue;
                    }
                    version = info;
                    break;
                }

                assert version != null;
                for(ModInfo info : version.coreMods) {
                    for(ModInfo currInfo : mods) {
                        if(!currInfo.name.equals(info.name)) {
                            continue;
                        }
                        if(currInfo.version.equals(info.version)) {
                            continue;
                        }

                        File mod = new File(gameDir + "/mods/" + modsDirName + "/" + info.name);
                        DownloadUtils.downloadFile(info.url, mod);
                    }
                }

                if(defaultMods) {
                    for (ModInfo info : version.defaultMods) {
                        for (ModInfo currInfo : mods) {
                            if (!currInfo.name.equals(info.name)) {
                                continue;
                            }
                            if (currInfo.version.equals(info.version)) {
                                continue;
                            }

                            File mod = new File(gameDir + "/mods/" + modsDirName + "/" + info.name + ".jar");
                            DownloadUtils.downloadFile(info.url, mod);
                        }
                    }
                }
                API_V1.finishedDownloading = true;
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Mods failed to download! Are you offline?\n" + e.getMessage());
                API_V1.finishedDownloading = true;
            }
        }
    }
}
