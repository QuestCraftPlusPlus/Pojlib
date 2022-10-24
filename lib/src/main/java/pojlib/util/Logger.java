package pojlib.util;

public enum Logger {
    INFO("[INFO]: "),
    DEBUG("[DEBUG]: "),
    ERROR("[ERROR]: ");

    private final String fmt;
    private static final boolean shouldLog = true;

    Logger(String fmt) {
        this.fmt = fmt;
    }

    public static void log(Logger logger, String message) {
        if (shouldLog) System.out.println(logger.fmt + message);
    }
}