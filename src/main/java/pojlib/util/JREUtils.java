package pojlib.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArrayMap;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import pojlib.API;

import pojlib.UnityPlayerActivity;
import pojlib.install.Installer;
import pojlib.install.MinecraftMeta;
import pojlib.util.json.MinecraftInstances;

public class JREUtils {
    private JREUtils() {}

    public static String LD_LIBRARY_PATH;
    public static String jvmLibraryPath;
    private static String sNativeLibDir;
    private static String runtimeDir;

    public static String findInLdLibPath(String libName) {
        if(Os.getenv("LD_LIBRARY_PATH")==null) {
            try {
                if (LD_LIBRARY_PATH != null) {
                    Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
                }
            }catch (ErrnoException e) {
                e.printStackTrace();
            }
            return libName;
        }
        for (String libPath : Os.getenv("LD_LIBRARY_PATH").split(":")) {
            File f = new File(libPath, libName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return libName;
    }

    public static boolean initJavaRuntime() {
        dlopen(findInLdLibPath("server/libjvm.so"));
        dlopen(findInLdLibPath("libverify.so"));
        dlopen(findInLdLibPath("libjava.so"));
        dlopen(findInLdLibPath("libnet.so"));
        dlopen(findInLdLibPath("libnio.so"));
        dlopen(findInLdLibPath("libawt.so"));
        dlopen(findInLdLibPath("libawt_headless.so"));
        dlopen(findInLdLibPath("libfreetype.so"));
        dlopen(findInLdLibPath("libfontmanager.so"));
        dlopen(findInLdLibPath("libjli.so"));

        String dlerr = dlerror();
        if(dlerr.contains(runtimeDir)) {
            Logger.getInstance().appendToLog("ERROR! Could not dlopen libraries! " + dlerr);
            return false;
        }

        return true;
    }

    public static boolean initializeExtraNatives(MinecraftInstances.Instance instance) {
        if(instance.extraNatives == null) {
            return false;
        }

        for(String nativeLib : instance.extraNatives.split(File.pathSeparator)) {
            dlopen(nativeLib);
        }

        String dlerr = dlerror();
        if(dlerr.contains(runtimeDir)) {
            Logger.getInstance().appendToLog("ERROR! Could not dlopen extra natives! " + dlerr);
            return false;
        }

        return true;
    }

    public static void redirectAndPrintJRELog() {
        Log.v("jrelog","Log starts here");
        JREUtils.logToLogger(Logger.getInstance());
        new Thread(new Runnable(){
            int failTime = 0;
            ProcessBuilder logcatPb;
            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        logcatPb = new ProcessBuilder().command("logcat", "-v", "brief", "-s", "jrelog:I", "LIBGL:I").redirectErrorStream(true);
                    }
                            Log.i("jrelog-logcat","Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat","Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.getInstance().appendToLog(currStr);
                    }
                            if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.getInstance().appendToLog("ERROR: Unable to get more log.");
                        }
                            }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.getInstance().appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat","Logcat thread started");
    }

    public static void relocateLibPath(final Context ctx, MinecraftInstances.Instance instance) {
        sNativeLibDir = ctx.getApplicationInfo().nativeLibraryDir;

        LD_LIBRARY_PATH = ctx.getFilesDir() + "/runtimes/JRE/bin:" + ctx.getFilesDir() + "/runtimes/JRE/lib:" +
                "/system/lib64:/vendor/lib64:/vendor/lib64/hw:" + ctx.getDataDir().toPath().resolve(instance.instanceName) + ":" +
                sNativeLibDir;
    }

    public static void setJavaEnvironment(Activity activity, MinecraftInstances.Instance instance) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("POJLIB_NATIVEDIR", activity.getApplicationInfo().nativeLibraryDir);
        envMap.put("JAVA_HOME", activity.getFilesDir() + "/runtimes/JRE");
        envMap.put("HOME", instance.gameDir);
        //envMap.put("APP_HOME", Constants.USER_HOME);
        envMap.put("TMPDIR", activity.getCacheDir().getAbsolutePath());
        envMap.put("VR_MODEL", API.model);
        envMap.put("POJLIB_RENDERER", "MobileGLUES");
        envMap.put("MG_DIR_PATH", activity.getFilesDir() + "/mg");

        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("PATH", activity.getFilesDir() + "/runtimes/JRE/bin:" + Os.getenv("PATH"));

        File mg = new File(activity.getFilesDir() + "/mg");
        mg.mkdirs();

        File customEnvFile = new File(Constants.USER_HOME, "custom_env.txt");
        if (customEnvFile.exists() && customEnvFile.isFile()) {
            BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Not use split() as only split first one
                int index = line.indexOf("=");
                envMap.put(line.substring(0, index), line.substring(index + 1));
            }
            reader.close();
        }
        envMap.put("LIBGL_ES", "2");
        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.getInstance().appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            Os.setenv(env.getKey(), env.getValue(), true);
        }

        File serverFile = new File(activity.getFilesDir() + "/runtimes/JRE/lib/server/libjvm.so");
        jvmLibraryPath = activity.getFilesDir() + "/runtimes/JRE/lib/" + (serverFile.exists() ? "server" : "client");
        Log.d("DynamicLoader","Base LD_LIBRARY_PATH: "+LD_LIBRARY_PATH);
        Log.d("DynamicLoader","Internal LD_LIBRARY_PATH: "+jvmLibraryPath+":"+LD_LIBRARY_PATH);
        setLdLibraryPath(jvmLibraryPath+":"+LD_LIBRARY_PATH);
    }

    // Called before game launch to ensure all files are present and correct
    public static boolean prelaunchCheck(Activity activity, MinecraftInstances.Instance instance) throws Throwable {
        runtimeDir = activity.getFilesDir() + "/runtimes/JRE";
        JREUtils.relocateLibPath(activity, instance);
        setJavaEnvironment(activity, instance);

        UnityPlayerActivity.installLWJGL(activity);
        Installer.installClient(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
        Installer.installLibraries(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
        Installer.installAssets(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();

        Installer.installJVM(activity, false);
        if(!initJavaRuntime()) {
            Installer.installJVM(activity, true);
            return initJavaRuntime();
        }

        return true;
    }

    public static int launchJavaVM(final Activity activity, final List<String> JVMArgs, MinecraftInstances.Instance instance) throws Throwable {
        final String graphicsLib = loadGraphicsLibrary();
        List<String> userArgs = getJavaArgs(activity, instance);

        //Add automatically generated args
        if (API.customRAMValue) {
            Logger.getInstance().appendToLog("Setting JVM memory to " + API.memoryValue + "MB (Custom)");
            userArgs.add("-Xms" + API.memoryValue + "M");
            userArgs.add("-Xmx" + API.memoryValue + "M");
        } else {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo ami = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(ami);
            long availMem = (ami.availMem-ami.threshold)/(1024*1024);
            long allocatedRam = Math.max(availMem, 1536);

            Logger.getInstance().appendToLog("Setting JVM memory to " + allocatedRam + "MB");

            userArgs.add("-Xms" + 1024 + "M");
            userArgs.add("-Xmx" + allocatedRam + "M");
        }

        if(!initializeExtraNatives(instance)) {
            Logger.getInstance().appendToLog("Some libraries did not dlopen properly. This can be safely ignored in most cases.");
        }

        // Garbage collection
        userArgs.add("-XX:+UnlockExperimentalVMOptions");
        userArgs.add("-XX:+UseZGC");
        userArgs.add("-XX:+ZGenerational");
        userArgs.add("-XX:-ZProactive");
        userArgs.add("-XX:+UnlockDiagnosticVMOptions");
        userArgs.add("-XX:+DisableExplicitGC");

        userArgs.add("-Dorg.lwjgl.opengl.libname=" + graphicsLib);
        userArgs.add("-Dorg.lwjgl.opengles.libname=" + "/system/lib64/libGLESv3.so");
        userArgs.add("-Dorg.lwjgl.egl.libname=" + "/system/lib64/libEGL_dri.so");

        userArgs.addAll(JVMArgs);
        System.out.println(JVMArgs);

        if (API.currentAcc != null && !API.currentAcc.uuid.isEmpty()) {
            System.out.println("UUID: " + API.currentAcc.uuid);
        } else {
            System.out.println("UUID is null! Make sure to log in!");
        }

        chdir(instance.gameDir);
        userArgs.add(0,"java"); //argv[0] is the program name according to C standard.

        int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
        Logger.getInstance().appendToLog("Java Exit code: " + exitCode);
        return exitCode;
    }

    private static void writeDNS(Context ctx, File out) throws IOException {
        FileWriter writer = new FileWriter(out);

        if(!API.hasConnection(ctx)) {
            writer.write("nameserver 8.8.8.8\n");
            writer.write("nameserver 8.8.4.4");
            writer.flush();
            writer.close();
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        LinkProperties lp = cm.getLinkProperties(activeNetwork);
        if(lp == null) {
            throw new IOException("Link properties are null!");
        }

        List<InetAddress> dnsServers = lp.getDnsServers();
        for (InetAddress dns : dnsServers) {
            writer.write(String.format("nameserver %s\n", dns.getHostAddress()));
        }
        writer.flush();
        writer.close();
    }

    /**
     *  Gives an argument list filled with both the user args
     *  and the auto-generated ones (eg. the window resolution).
     * @param ctx The application context
     * @return A list filled with args.
     */
    public static List<String> getJavaArgs(Context ctx, MinecraftInstances.Instance instance) {
        File resConfFile = new File(Constants.USER_HOME + "/hacks/resolv.conf");
        try {
            if(!resConfFile.exists()) {
                resConfFile.createNewFile();
            }
            writeDNS(ctx, resConfFile);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Couldn't write DNS servers! " + e.getMessage());
        }
        return new ArrayList<>(Arrays.asList(
                "-Djava.home=" + new File(ctx.getFilesDir(), "runtimes/JRE"),
                "-Djava.io.tmpdir=" + ctx.getCacheDir().getAbsolutePath(),
                "-Duser.home=" + instance.gameDir,
                "-Duser.language=" + System.getProperty("user.language"),
                "-Dos.name=Linux",
                "-Dos.version=Android-" + Build.VERSION.RELEASE,
                "-Dorg.lwjgl.librarypath=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Djna.boot.library.path=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Djna.nosys=true",
                "-Djava.library.path=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Dglfwstub.windowWidth=" + 1280,
                "-Dglfwstub.windowHeight=" + 720,
                "-Dglfwstub.initEgl=false",
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation
                "-Dnet.minecraft.clientmodname=" + "QuestCraft",
                "-Dext.net.resolvPath=" + resConfFile,
                "-Dsodium.checks.issue2561=false",
                "-Dorg.sqlite.lib.path=" + ctx.getApplicationInfo().nativeLibraryDir
        ));
    }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary(){
        return "libmobileglues.so";
    }

    public static native long getEGLContextPtr();
    public static native long getEGLDisplayPtr();
    public static native long getEGLConfigPtr();
    public static native int chdir(String path);
    public static native void logToLogger(final Logger logger);
    public static native boolean dlopen(String libPath);
    public static native String dlerror();
    public static native void setLdLibraryPath(String ldLibraryPath);

    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("istdio");
    }
}
