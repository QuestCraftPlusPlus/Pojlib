package pojlib.api;

import android.app.Activity;
import pojlib.account.MinecraftAccount;

public class PluginInstance {

    public int Add(int i, int j) {
        return i + j;
    }

    private static Activity unityActivity;

    public static void recieveUnityActivity(Activity tActivity) {
        unityActivity = tActivity;
    }

}
