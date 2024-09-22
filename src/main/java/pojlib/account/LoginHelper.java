package pojlib.account;

import android.app.Activity;
import android.util.Log;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import pojlib.API;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;
import pojlib.util.MSAException;

public class LoginHelper {
    public static final Set<String> SCOPES;
    private static Thread loginThread;
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

        SCOPES = new HashSet<>();
        SCOPES.add("XboxLive.SignIn");
        SCOPES.add("XboxLive.offline_access");
    }

    public static MinecraftAccount refreshAccount(Activity activity) {
        Set<IAccount> accountsInCache = pca.getAccounts().join();
        IAccount account = accountsInCache.iterator().next();

        IAuthenticationResult result;
        try {
            SilentParameters silentParameters =
                    SilentParameters
                            .builder(SCOPES, account)
                            .build();

            result = pca.acquireTokenSilently(silentParameters).join();
            MinecraftAccount acc = new Msa(activity).performLogin(result.accessToken());
            GsonUtils.objectToJsonFile(activity.getFilesDir() + "/accounts/account.json", acc);
            return acc;
        } catch (Exception ex) {
            Logger.getInstance().appendToLog("Couldn't refresh token! " + ex);
            return null;
        }
    }

    public static void login(Activity activity) {
        loginThread = new Thread(() -> {
            Consumer<DeviceCode> deviceCodeConsumer = (DeviceCode deviceCode) -> API.msaMessage = deviceCode.message();
            CompletableFuture<IAuthenticationResult> future = pca.acquireToken(
                    DeviceCodeFlowParameters.builder(SCOPES, deviceCodeConsumer).build());

            try {
                IAuthenticationResult res = future.get();
                while(res.account() == null) {
                    Thread.sleep(20);
                }
                try {
                    API.currentAcc = MinecraftAccount.login(activity, activity.getFilesDir() + "/accounts", res.accessToken());
                } catch (IOException | JSONException | MSAException e) {
                    Logger.getInstance().appendToLog("Unable to load account! | " + e);
                }
                API.profileImage = MinecraftAccount.getSkinFaceUrl(API.currentAcc);
                API.profileName = API.currentAcc.username;
            } catch (ExecutionException | InterruptedException e) {
                Logger.getInstance().appendToLog("MicrosoftLogin | Something went wrong! Couldn't reach the Microsoft Auth servers.");
                API.msaMessage = "MicrosoftLogin | Something went wrong! Couldn't reach the Microsoft Auth servers.";
            }
        });

        loginThread.start();
    }
}
