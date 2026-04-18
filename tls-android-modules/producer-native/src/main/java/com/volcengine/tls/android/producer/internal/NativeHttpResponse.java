package com.volcengine.tls.android.producer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NativeHttpResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final int errorCode;
    private final String errorMessage;

    public NativeHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this(statusCode, headers, body, 0, null);
    }

    public NativeHttpResponse(
            int statusCode,
            Map<String, List<String>> headers,
            byte[] body,
            int errorCode,
            String errorMessage) {
        this.statusCode = statusCode;
        this.headers = copyHeaders(headers);
        this.body = body == null ? new byte[0] : body.clone();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String getRequestId() {
        return getHeaderFirstIgnoreCase("x-tls-requestid", "x-request-id");
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            List<String> values = entry.getValue();
            result.put(key, values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values)));
        }
        return Collections.unmodifiableMap(result);
    }

    private String getHeaderFirstIgnoreCase(String... candidates) {
        if (headers.isEmpty() || candidates == null) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            for (String candidate : candidates) {
                if (candidate != null && candidate.equalsIgnoreCase(key)) {
                    List<String> values = entry.getValue();
                    if (values != null && !values.isEmpty()) {
                        String value = values.get(0);
                        if (value != null && !value.isEmpty()) {
                            return value;
                        }
                    }
                }
            }
        }
        return null;
    }
}
