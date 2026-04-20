package android.util;

public final class Log {
    public static volatile String lastMethod;
    public static volatile String lastTag;
    public static volatile String lastMsg;
    public static volatile Throwable lastThrowable;

    private Log() {}

    public static void reset() {
        lastMethod = null;
        lastTag = null;
        lastMsg = null;
        lastThrowable = null;
    }

    public static int d(String tag, String msg) {
        lastMethod = "d";
        lastTag = tag;
        lastMsg = msg;
        lastThrowable = null;
        return 0;
    }

    public static int i(String tag, String msg) {
        lastMethod = "i";
        lastTag = tag;
        lastMsg = msg;
        lastThrowable = null;
        return 0;
    }

    public static int w(String tag, String msg) {
        lastMethod = "w";
        lastTag = tag;
        lastMsg = msg;
        lastThrowable = null;
        return 0;
    }

    public static int e(String tag, String msg) {
        lastMethod = "e";
        lastTag = tag;
        lastMsg = msg;
        lastThrowable = null;
        return 0;
    }

    public static int e(String tag, String msg, Throwable t) {
        lastMethod = "et";
        lastTag = tag;
        lastMsg = msg;
        lastThrowable = t;
        return 0;
    }
}
