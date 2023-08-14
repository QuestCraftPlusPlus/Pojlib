package pojlib.util;

import android.app.Activity;

import com.microsoft.aad.msal4j.AuthorizationCodeParameters;
import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import pojlib.account.MinecraftAccount;
import pojlib.api.API_V1;

public class LoginHelper {
    public static Thread loginThread;

    private static final PublicClientApplication pca;

    static {
        try {
            pca = PublicClientApplication.builder("d17a73a2-707c-40f5-8c90-d3eda0956f10")
                    .authority("https://login.microsoftonline.com/consumers/")
                    .build();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static MinecraftAccount getNewToken(Activity activity) {
        CompletableFuture<IAuthenticationResult> future;
        try {
            future = pca.acquireTokenSilently(SilentParameters.builder(Set.of("clientId")).build());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        try {
            IAuthenticationResult res = future.get();
            while (res.account() == null) ;
            return MinecraftAccount.load(activity.getFilesDir() + "/accounts", res.accessToken());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void beginLogin(Activity activity) {
        loginThread = new Thread(() -> {
            Consumer<DeviceCode> deviceCodeConsumer = (DeviceCode deviceCode) -> API_V1.msaMessage = deviceCode.message();
            CompletableFuture<IAuthenticationResult> future = pca.acquireToken(
                    DeviceCodeFlowParameters.builder(Set.of("XboxLive.SignIn", "XboxLive.offline_access"), deviceCodeConsumer).build());

            try {
                IAuthenticationResult res = future.get();
                while(res.account() == null);
                try {
                    API_V1.currentAcc = MinecraftAccount.login(activity.getFilesDir() + "/accounts", new String[]{res.accessToken(), String.valueOf(res.expiresOnDate().getTime())});
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
                API_V1.profileImage = MinecraftAccount.getSkinFaceUrl(API_V1.currentAcc);
                API_V1.profileName = API_V1.currentAcc.username;
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        loginThread.start();
    }
}
