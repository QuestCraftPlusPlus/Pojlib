package pojlib.util;

import android.content.Context;

public class VLoader {
    static {
        System.loadLibrary("openvr_api");
    }

    public static native void setAndroidInitInfo(Context ctx);
    public static native void setEGLGlobal(long context, long display, long config);
}
