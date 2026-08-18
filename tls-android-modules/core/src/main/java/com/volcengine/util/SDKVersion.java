package com.volcengine.util;

import java.io.IOException;
import java.util.Properties;

public class SDKVersion {
    private static String VERSION = "Unknown";
    private static String AGENT = "volc-sdk-android/Unknown";
    private static String MODULE = "android";
    static {
        final Properties properties = new Properties();
        try {
            java.io.InputStream in = SDKVersion.class.getClassLoader().getResourceAsStream("com/volcengine/version");
            if (in != null) {
                properties.load(in);
                String v = properties.getProperty("version");
                if (v != null && !v.isEmpty()) { VERSION = v; }
                String m = properties.getProperty("module");
                if (m != null && !m.isEmpty()) { MODULE = m; }
            }
        } catch (IOException e) { }
        AGENT = buildAgent(MODULE);
    }
    public static String getVERSION() { return VERSION; }
    public static String getAGENT() { return AGENT; }
    public static String getAGENT(String module) {
        if (module == null || module.isEmpty()) { return AGENT; }
        return buildAgent(module);
    }
    public static String getMODULE() { return MODULE; }

    private static String buildAgent(String module) {
        return "volc-tls-android/" + module + "/v" + VERSION;
    }
}
