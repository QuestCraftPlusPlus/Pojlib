package pojlib.util;

import androidx.annotation.Nullable;
import pojlib.API;

public class MSAException extends RuntimeException {
    public MSAException(String msaMessage, @Nullable Throwable cause)  {
        super(msaMessage, cause);
        API.msaMessage = msaMessage;
    }
}
