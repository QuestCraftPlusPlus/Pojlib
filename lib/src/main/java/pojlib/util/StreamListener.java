package pojlib.util;

import java.util.EventListener;

public interface StreamListener extends EventListener {
    public void byteReceived(int b, int count);
}
