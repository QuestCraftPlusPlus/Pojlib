package pojlib.api;

import android.app.Activity;
import com.google.gson.JsonObject;
import pojlib.util.GsonUtils;

public class PluginInstance {

    public int Add(int i, int j) {
        return i + j;
    }

    public static Activity unityActivity;

    public static void receiveUnityActivity(Activity tActivity) {
        unityActivity = tActivity;
    }

    public static void testFunction2() {
        JsonObject object = new JsonObject();
        object.addProperty("pog", "champ");
        GsonUtils.objectToJsonFile("test.json", object);
    }

}
