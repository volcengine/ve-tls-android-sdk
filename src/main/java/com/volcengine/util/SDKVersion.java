package com.volcengine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class SDKVersion {

    private static final Logger LOG = LoggerFactory.getLogger(SDKVersion.class);
    private static String VERSION = "Unknown";

    private static String AGENT = "volc-sdk-java/Unknown";

    static {
        final Properties properties = new Properties();
        try {
            properties.load(SDKVersion.class.getClassLoader().getResourceAsStream("com/volcengine/version"));
            VERSION = properties.getProperty("version");
            AGENT = "volc-sdk-android/v" + VERSION;
        } catch (IOException e) {
            LOG.error("Read file version file fail.");
        }
    }

    public static String getVERSION() {
        return VERSION;
    }

    public static String getAGENT() {
        return AGENT;
    }

}
