package pojlib.util;

import android.content.Context;

public class VLoader {
    static {
        System.loadLibrary("openxr_loader");
        System.loadLibrary("openvr_api");
    }

    public static native void setAndroidInitInfo(Context ctx);
}
