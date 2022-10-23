package pojlib.unity.util;

import pojlib.unity.authentication.MinecraftAccount;

public interface RefreshListener
{
    public void onFailed(Throwable e);
    public void onSuccess(MinecraftAccount profile);
}
