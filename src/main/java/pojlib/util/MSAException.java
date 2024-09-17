package pojlib.util;

import pojlib.API;

public class MSAException extends Exception {
    public MSAException(String msaMessage)  {
        API.msaMessage = msaMessage;
    }
}
