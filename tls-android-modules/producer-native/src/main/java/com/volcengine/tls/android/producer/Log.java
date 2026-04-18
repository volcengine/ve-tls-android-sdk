package com.volcengine.tls.android.producer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Log {
    private final Map<String, String> content = new LinkedHashMap<>();
    private long logTime = System.currentTimeMillis();

    public Log putContent(String key, String value) {
        content.put(key, value);
        return this;
    }

    public Log putContents(Map<String, String> contents) {
        if (contents == null) {
            return this;
        }
        content.putAll(contents);
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
}
