package pojlib.util;

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TokenPersistence implements ITokenCacheAccessAspect {
        String data;
        File cache;

        TokenPersistence(String data, File cache) {
                this.data = data;
                this.cache = cache;
        }

        @Override
        public void beforeCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
                iTokenCacheAccessContext.tokenCache().deserialize(data);
        }

        @Override
        public void afterCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
                data = iTokenCacheAccessContext.tokenCache().serialize();
                try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(cache));
                        writer.write(data);
                        writer.flush();
                        writer.close();
                } catch (IOException e) {
                        throw new RuntimeException(e);
                }
        }
}