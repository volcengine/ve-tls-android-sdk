package com.volcengine.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public final class TlsLoggerFactory {
    private static final ConcurrentMap<String, TlsLogger> CACHE = new ConcurrentHashMap<>();
    private static volatile TlsLoggerProvider PROVIDER;
    private static final String ANDROID_LOG_CLASS = "android.util.Log";
    private static final String DEFAULT_TAG = "TLS-SDK";

    private TlsLoggerFactory() {}

    public static TlsLogger getLogger(Class<?> cls) {
        if (cls == null) { return getLogger("unknown"); }
        return getLogger(cls.getName());
    }

    public static TlsLogger getLogger(String name) {
        if (name == null || name.isEmpty()) { name = "unknown"; }
        final String key = name;
        TlsLogger logger = CACHE.get(key);
        if (logger != null) {
            return logger;
        }
        logger = create(key);
        TlsLogger existing = CACHE.putIfAbsent(key, logger);
        return existing == null ? logger : existing;
    }

    public static void setProvider(TlsLoggerProvider provider) {
        PROVIDER = provider;
        CACHE.clear();
    }

    private static TlsLogger create(String name) {
        TlsLoggerProvider provider = PROVIDER;
        if (provider != null) {
            try {
                TlsLogger logger = provider.getLogger(name);
                if (logger != null) {
                    return logger;
                }
            } catch (Throwable ignored) {
            }
        }
        if (isAndroidRuntime()) {
            return new AndroidTlsLogger(name);
        }
        return new JulTlsLogger(name);
    }

    private static boolean isAndroidRuntime() {
        try {
            Class.forName(ANDROID_LOG_CLASS);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class AndroidTlsLogger implements TlsLogger {
        private final String name;
        private final Object logClass;
        private final java.lang.reflect.Method d;
        private final java.lang.reflect.Method i;
        private final java.lang.reflect.Method w;
        private final java.lang.reflect.Method e;
        private final java.lang.reflect.Method et;

        AndroidTlsLogger(String name) {
            this.name = name;
            try {
                Class<?> c = Class.forName(ANDROID_LOG_CLASS);
                this.logClass = c;
                this.d = c.getMethod("d", String.class, String.class);
                this.i = c.getMethod("i", String.class, String.class);
                this.w = c.getMethod("w", String.class, String.class);
                this.e = c.getMethod("e", String.class, String.class);
                this.et = c.getMethod("e", String.class, String.class, Throwable.class);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override public void debug(String msg) { invoke(d, msg); }
        @Override public void debug(String format, Object... args) { invoke(d, format(format, args), throwableOrNull(args)); }
        @Override public void info(String msg) { invoke(i, msg); }
        @Override public void info(String format, Object... args) { invoke(i, format(format, args), throwableOrNull(args)); }
        @Override public void warn(String msg) { invoke(w, msg); }
        @Override public void warn(String format, Object... args) { invoke(w, format(format, args), throwableOrNull(args)); }
        @Override public void error(String msg) { invoke(e, msg); }
        @Override public void error(String msg, Throwable t) { invoke(e, msg, t); }
        @Override public void error(String format, Object... args) { invoke(e, format(format, args), throwableOrNull(args)); }

        private void invoke(java.lang.reflect.Method m, String msg) { invoke(m, msg, null); }

        private void invoke(java.lang.reflect.Method m, String msg, Throwable t) {
            if (m == null) { return; }
            String out = prefix(msg);
            try {
                if (t != null && m.equals(e)) {
                    et.invoke(logClass, DEFAULT_TAG, out, t);
                } else {
                    m.invoke(logClass, DEFAULT_TAG, out);
                }
            } catch (Throwable ignored) {
            }
        }

        private String prefix(String msg) {
            if (msg == null) { msg = ""; }
            return "[" + name + "] " + msg;
        }
    }

    private static final class JulTlsLogger implements TlsLogger {
        private final java.util.logging.Logger logger;

        JulTlsLogger(String name) {
            this.logger = java.util.logging.Logger.getLogger(name);
        }

        @Override public void debug(String msg) { logger.log(Level.FINE, msg); }
        @Override public void debug(String format, Object... args) { log(Level.FINE, format, args); }
        @Override public void info(String msg) { logger.log(Level.INFO, msg); }
        @Override public void info(String format, Object... args) { log(Level.INFO, format, args); }
        @Override public void warn(String msg) { logger.log(Level.WARNING, msg); }
        @Override public void warn(String format, Object... args) { log(Level.WARNING, format, args); }
        @Override public void error(String msg) { logger.log(Level.SEVERE, msg); }
        @Override public void error(String msg, Throwable t) { logger.log(Level.SEVERE, msg, t); }
        @Override public void error(String format, Object... args) { log(Level.SEVERE, format, args); }

        private void log(Level level, String format, Object... args) {
            Throwable t = throwableOrNull(args);
            String s = format(format, args);
            if (t != null) {
                logger.log(level, s, t);
            } else {
                logger.log(level, s);
            }
        }
    }

    private static Throwable throwableOrNull(Object[] args) {
        if (args == null || args.length == 0) { return null; }
        Object last = args[args.length - 1];
        if (last instanceof Throwable) { return (Throwable) last; }
        return null;
    }

    private static String format(String format, Object[] args) {
        if (format == null) { return ""; }
        if (args == null || args.length == 0) { return format; }
        int argLen = args.length;
        if (args[argLen - 1] instanceof Throwable) { argLen -= 1; }
        if (argLen <= 0) { return format; }
        StringBuilder sb = new StringBuilder(format.length() + argLen * 8);
        int argIndex = 0;
        int i = 0;
        while (i < format.length()) {
            int j = format.indexOf("{}", i);
            if (j == -1) {
                sb.append(format, i, format.length());
                break;
            }
            sb.append(format, i, j);
            if (argIndex < argLen) {
                Object a = args[argIndex++];
                sb.append(String.valueOf(a));
            } else {
                sb.append("{}");
            }
            i = j + 2;
        }
        return sb.toString();
    }
}
