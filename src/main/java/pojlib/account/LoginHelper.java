package pojlib.account;

import android.app.Activity;

import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import pojlib.API;
import pojlib.util.Constants;
import pojlib.util.Logger;
import pojlib.util.MSAException;

public class LoginHelper {
    public static Thread loginThread;

    private static PublicClientApplication pca;

    static {
        try {
            File cache = new File(Constants.USER_HOME + "/cache_data/serialized_cache.json");
            if(!cache.exists()) {
                cache.getParentFile().mkdirs();
                cache.createNewFile();
            }

            // Loads cache from file
            BufferedReader reader = new BufferedReader(new FileReader(cache.getPath()));
            String dataToInitCache = reader.readLine();
            reader.close();

            TokenPersistence persistenceAspect = new TokenPersistence(dataToInitCache, cache);

            pca = PublicClientApplication.builder("d17a73a2-707c-40f5-8c90-d3eda0956f10")
                    .setTokenCacheAccessAspect(persistenceAspect)
                    .authority("https://login.microsoftonline.com/consumers/")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MinecraftAccount getNewToken(Activity activity) {
        CompletableFuture<IAuthenticationResult> future;
        try {
            Set<IAccount> accounts = pca.getAccounts().join();
            if (accounts.isEmpty()) {
                Logger.getInstance().appendToLog("Error!: QuestCraft account not set!");
                beginLogin(activity);
                throw new RuntimeException("Error!: QuestCraft account not set!");
            }
            IAccount account = accounts.iterator().next();
            HashSet<String> params = new HashSet<>();
            params.add("XboxLive.SignIn");
            params.add("XboxLive.offline_access");
            future = pca.acquireTokenSilently(SilentParameters.builder(params, account).build());
        } catch (MalformedURLException e) {
            Logger.getInstance().appendToLog(e.toString());
            throw new RuntimeException(e);
        }

        try {
            IAuthenticationResult res = future.get();
            return MinecraftAccount.load(activity.getFilesDir() + "/accounts", res.accessToken(), String.valueOf(res.expiresOnDate().getTime()));
        } catch (ExecutionException | InterruptedException e) {
            Logger.getInstance().appendToLog(e.toString());
            throw new RuntimeException(e);
        }
    }

    public static void beginLogin(Activity activity) {
        loginThread = new Thread(() -> {
            Consumer<DeviceCode> deviceCodeConsumer = (DeviceCode deviceCode) -> API.msaMessage = deviceCode.message();
            HashSet<String> params = new HashSet<>();
            params.add("XboxLive.SignIn");
            params.add("XboxLive.offline_access");
            CompletableFuture<IAuthenticationResult> future = pca.acquireToken(
                    DeviceCodeFlowParameters.builder(params, deviceCodeConsumer).build());

            try {
                IAuthenticationResult res = future.get();
                while(res.account() == null);
                try {
                    API.currentAcc = MinecraftAccount.login(activity.getFilesDir() + "/accounts", new String[]{res.accessToken(), String.valueOf(res.expiresOnDate().getTime())});
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
                API.profileImage = MinecraftAccount.getSkinFaceUrl(API.currentAcc);
                API.profileName = API.currentAcc.username;
            } catch (ExecutionException | InterruptedException e) {
                throw new MSAException("MicrosoftLogin | Something went wrong! Couldn't reach the Microsoft Auth servers.", e);
            }
        });

        loginThread.start();
    }
}
