package com.volcengine.tls.android.producer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Log {
    private final Map<String, String> content = new LinkedHashMap<>();
    private long logTime = System.currentTimeMillis();

    public Log putContent(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("log content key cannot be null");
        }
        content.put(key, normalizeValue(value));
        return this;
    }

    public Log putContents(Map<String, String> contents) {
        if (contents == null) {
            return this;
        }
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            putContent(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public Map<String, String> getContent() {
        return Collections.unmodifiableMap(content);
    }

    public long getLogTime() {
        return logTime;
    }

    public Log setLogTime(long logTime) {
        this.logTime = logTime;
        return this;
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value;
    }
}
