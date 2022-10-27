package pojlib.util;

import android.os.Environment;

import java.io.File;

public class Constants {

    public static String MOJANG_META_URL = "https://piston-meta.mojang.com";
    public static String MOJANG_RESOURCES_URL = "https://resources.download.minecraft.net";
    
    public static String FABRIC_META_URL = "https://meta.fabricmc.net/v2";
    public static String QUILT_META_URL = "https://meta.quiltmc.org/v3";

    public static String OAUTH_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    public static String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    public static String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static String MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore";
    public static String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    public static String MC_DIR = new File(Environment.getExternalStorageDirectory(),"Android/data/com.qcxr.qcxr/files/.minecraft").getAbsolutePath();
    public static String USER_HOME = new File(Environment.getExternalStorageDirectory(),"Android/data/com.qcxr.qcxr/files").getAbsolutePath();

    public static String CRAFATAR_URL = "https://crafatar.com";
}