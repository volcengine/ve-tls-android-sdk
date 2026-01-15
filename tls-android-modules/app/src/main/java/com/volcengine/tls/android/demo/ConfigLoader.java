package com.volcengine.tls.android.demo;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final String FILE_NAME = "tls_config.properties";
    public static Properties load(Context ctx) {
        Properties p = new Properties();
        InputStream in = null;
        try {
            File ext = new File(ctx.getExternalFilesDir(null), FILE_NAME);
            if (ext.exists()) { in = new FileInputStream(ext); }
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
}
