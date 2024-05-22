package pojlib.util.json;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;
import pojlib.InstanceHandler;
import pojlib.util.Constants;
import pojlib.util.DownloadUtils;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;

public class MinecraftInstances {
    public Instance[] instances;

    public Instance load(String name) {
        for(Instance instance : instances) {
            if(instance.instanceName.equals(name)) {
                return instance;
            }
        }

        return null;
    }

    public Instance[] toArray() {
        if(instances == null) {
            return new Instance[0];
        }
        return instances;
    }

    public static class Instance {
        public String instanceName;
        public String instanceImageURL;
        public String versionName;
        public String versionType;
        public String classpath;
        public String gameDir;
        public String assetIndex;
        public String assetsDir;
        public String mainClass;
        public ProjectInfo[] extProjects;
        public boolean defaultMods;

        public List<String> generateLaunchArgs(MinecraftAccount account) {
            String[] mcArgs = {"--username", account.username, "--version", versionName, "--gameDir", gameDir,
                    "--assetsDir", assetsDir, "--assetIndex", assetIndex, "--uuid", account.uuid.replaceAll("-", ""),
                    "--accessToken", account.accessToken, "--userType", account.userType, "--versionType", "release"};

            List<String> allArgs = new ArrayList<>(Arrays.asList("-cp", classpath));
            allArgs.add(mainClass);
            allArgs.addAll(Arrays.asList(mcArgs));
            return allArgs;
        }

        public ProjectInfo[] toArray() {
            if(extProjects == null) {
                return new ProjectInfo[0];
            }
            return extProjects;
        }

        private ModsJson parseModsJson(String gameDir) throws Exception {
            File mods = new File(gameDir + "/mods.json");
            if(API_V1.developerMods) {
                DownloadUtils.downloadFile(InstanceHandler.DEV_MODS, mods);
            } else {
                DownloadUtils.downloadFile(InstanceHandler.MODS, mods);
            }

            return GsonUtils.jsonFileToObject(mods.getAbsolutePath(), ModsJson.class);
        }

        public void updateMods(MinecraftInstances instances) {
            API_V1.finishedDownloading = false;
            if(extProjects == null) {
                extProjects = new ProjectInfo[0];
            }
            try {
                ModsJson modsJson = parseModsJson(Constants.USER_HOME);

                ModsJson.Version version = null;
                for(ModsJson.Version info : modsJson.versions) {
                    if(info.name.equals(versionName)) {
                        version = info;
                        break;
                    }
                }

                assert version != null;

                File modsDir = new File(gameDir + "/mods");
                if(!modsDir.exists()) {
                    ArrayList<ProjectInfo> modInfos = new ArrayList<>();
                    for(ProjectInfo info : version.coreMods) {
                        File mod = new File(modsDir + "/" + info.slug + ".jar");
                        DownloadUtils.downloadFile(info.download_link, mod);
                        info.type = "mod";
                        modInfos.add(info);
                    }
                    if(defaultMods) {
                        for (ProjectInfo info : version.defaultMods) {
                            File mod = new File(modsDir + "/" + info.slug + ".jar");
                            DownloadUtils.downloadFile(info.download_link, mod);
                            info.type = "mod";
                            modInfos.add(info);
                        }
                    }
                    extProjects = modInfos.toArray(modInfos.toArray(new ProjectInfo[0]));
                    GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
                    API_V1.finishedDownloading = true;
                    return;
                }

                for(ProjectInfo info : version.coreMods) {
                    for(ProjectInfo currInfo : extProjects) {
                        if(!currInfo.slug.equals(info.slug)) {
                            continue;
                        }
                        if(currInfo.version.equals(info.version)) {
                            continue;
                        }

                        File mod = new File(modsDir + "/" + info.slug + ".jar");
                        DownloadUtils.downloadFile(info.download_link, mod);
                        info = currInfo;
                    }
                }

                if(defaultMods) {
                    for (ProjectInfo info : version.defaultMods) {
                        for (ProjectInfo currInfo : extProjects) {
                            if (!currInfo.slug.equals(info.slug)) {
                                continue;
                            }
                            if (currInfo.version.equals(info.version)) {
                                continue;
                            }

                            File mod = new File(modsDir + "/" + info.slug + ".jar");
                            DownloadUtils.downloadFile(info.download_link, mod);
                            info = currInfo;
                        }
                    }
                }

                // Download custom mods
                for(ProjectInfo currInfo : extProjects) {
                    boolean isMod = currInfo.type.equals("mod");
                    File mod = new File((isMod ? modsDir : gameDir + "/resourcepacks") + "/" + currInfo.slug + (isMod ? ".jar" : ".zip"));
                    if(!mod.exists()) {
                        DownloadUtils.downloadFile(currInfo.download_link, mod);
                    }
                }

                GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
                API_V1.finishedDownloading = true;
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Mods failed to download! Are you offline?\n" + e);
                API_V1.finishedDownloading = true;
            }
        }
    }
}
