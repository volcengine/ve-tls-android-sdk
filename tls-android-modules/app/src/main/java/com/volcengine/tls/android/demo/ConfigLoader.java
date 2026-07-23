package com.volcengine.tls.android.demo;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final String FILE_NAME = "tls_config.properties";

    public static Properties load(Context ctx) {
        Properties p = new Properties();
        InputStream in = null;
        try {
            File ext = getExternalConfigFile(ctx);
            if (ext != null && ext.exists()) { in = new FileInputStream(ext); }
            if (in == null) {
                File internal = new File(ctx.getFilesDir(), FILE_NAME);
                if (internal.exists()) { in = new FileInputStream(internal); }
            }
            if (in != null) { p.load(in); }
        } catch (Exception ignored) { }
        return p;
    }
    public static String get(Properties p, String key) {
        String v = p.getProperty(key);
        if (v != null && v.length() > 0) return v;
        String sys = System.getProperty("tls." + key);
        if (sys != null && sys.length() > 0) return sys;
        String env = System.getenv(key);
        if (env != null && env.length() > 0) return env;
        return null;
    }

    public static boolean hasRequiredConfig(Properties p) {
        return !isBlank(get(p, "endPoint"))
                && !isBlank(get(p, "region"))
                && !isBlank(get(p, "ak"))
                && !isBlank(get(p, "sk"))
                && !isBlank(get(p, "topicId"));
    }

    public static String normalizeCompressValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if ("lz4".equalsIgnoreCase(trimmed)) {
            return "lz4";
        }
        if ("none".equalsIgnoreCase(trimmed)) {
            return "none";
        }
        return "";
    }

    public static void save(Context ctx, Properties p) throws Exception {
        File file = resolveConfigFile(ctx);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create config directory: " + parent);
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            p.store(out, "TLS demo config");
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) { }
            }
        }
    }

    public static File resolveConfigFile(Context ctx) {
        File ext = getExternalConfigFile(ctx);
        if (ext != null) {
            return ext;
        }
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    private static File getExternalConfigFile(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) {
            return null;
        }
        return new File(base, FILE_NAME);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
