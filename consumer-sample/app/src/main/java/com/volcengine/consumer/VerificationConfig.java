package com.volcengine.consumer;

import com.volcengine.tls.android.producer.LogProducerConfig;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class VerificationConfig {
    enum Scenario {
        SEND_ONCE("send-once"),
        BURST("burst"),
        UPDATE_ENDPOINT_SMOKE("update-endpoint-smoke");

        private final String wireName;

        Scenario(String wireName) {
            this.wireName = wireName;
        }

        String getWireName() {
            return wireName;
        }

        static Scenario fromValue(String value) {
            if (value == null || value.isEmpty()) {
                return SEND_ONCE;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            for (Scenario scenario : values()) {
                if (scenario.wireName.equals(normalized)) {
                    return scenario;
                }
            }
            throw new IllegalArgumentException("unsupported scenario: " + value);
        }
    }

    private final Scenario scenario;
    private final String endpoint;
    private final String region;
    private final String topicId;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String securityToken;
    private final String compress;
    private final int sendCount;
    private final int callbackTimeoutMs;
    private final int retryCount;
    private final int sendThreadCount;
    private final boolean persistent;
    private final String persistentFilePath;
    private final String updateEndpoint;
    private final String updateRegion;
    private final String updateTopicId;

    private VerificationConfig(
            Scenario scenario,
            String endpoint,
            String region,
            String topicId,
            String accessKeyId,
            String accessKeySecret,
            String securityToken,
            String compress,
            int sendCount,
            int callbackTimeoutMs,
            int retryCount,
            int sendThreadCount,
            boolean persistent,
            String persistentFilePath,
            String updateEndpoint,
            String updateRegion,
            String updateTopicId) {
        this.scenario = scenario;
        this.endpoint = endpoint;
        this.region = region;
        this.topicId = topicId;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.securityToken = securityToken;
        this.compress = compress;
        this.sendCount = sendCount;
        this.callbackTimeoutMs = callbackTimeoutMs;
        this.retryCount = retryCount;
        this.sendThreadCount = sendThreadCount;
        this.persistent = persistent;
        this.persistentFilePath = persistentFilePath;
        this.updateEndpoint = updateEndpoint;
        this.updateRegion = updateRegion;
        this.updateTopicId = updateTopicId;
    }

    static VerificationConfig fromMap(Map<String, String> values, Map<String, String> defaults) {
        Map<String, String> merged = new HashMap<>(defaults);
        merged.putAll(values);
        Scenario scenario = Scenario.fromValue(merged.get("scenario"));
        String compress = normalizeCompress(merged.get("compress"));
        int defaultCount = scenario == Scenario.BURST ? 20 : 1;
        int sendCount = parsePositiveInt(merged.get("sendCount"), defaultCount, "sendCount");
        int callbackTimeoutMs = parsePositiveInt(merged.get("callbackTimeoutMs"), 15000, "callbackTimeoutMs");
        int retryCount = parseNonNegativeInt(merged.get("retryCount"), 0, "retryCount");
        int sendThreadCount = parsePositiveInt(merged.get("sendThreadCount"), 1, "sendThreadCount");
        boolean persistent = parseBoolean(merged.get("persistent"));
        return new VerificationConfig(
                scenario,
                requireText(merged, "endpoint"),
                requireText(merged, "region"),
                requireText(merged, "topicId"),
                requireText(merged, "ak"),
                requireText(merged, "sk"),
                merged.getOrDefault("token", ""),
                compress,
                sendCount,
                callbackTimeoutMs,
                retryCount,
                sendThreadCount,
                persistent,
                merged.get("persistentFilePath"),
                merged.get("updateEndpoint"),
                merged.get("updateRegion"),
                merged.get("updateTopicId"));
    }

    LogProducerConfig toProducerConfig() {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(endpoint)
                .setRegion(region)
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setSecurityToken(securityToken)
                .setTopicId(topicId)
                .setSendThreadCount(sendThreadCount)
                .setRetryMaxAttempts(retryCount)
                .setPacketLogBytes(128 * 1024)
                .setPacketLogCount(Math.max(32, sendCount))
                .setPacketTimeoutMs(Math.min(callbackTimeoutMs, 3000));
        if ("none".equals(compress)) {
            config.setCompressType(LogProducerConfig.CompressType.NONE);
        } else {
            config.setCompressType(LogProducerConfig.CompressType.LZ4);
        }
        if (persistent) {
            config.setPersistent(true);
            if (persistentFilePath != null && !persistentFilePath.isEmpty()) {
                config.setPersistentFilePath(persistentFilePath);
            }
        }
        return config;
    }

    Scenario getScenario() {
        return scenario;
    }

    String getEndpoint() {
        return endpoint;
    }

    String getRegion() {
        return region;
    }

    String getTopicId() {
        return topicId;
    }

    String getCompress() {
        return compress;
    }

    int getSendCount() {
        return sendCount;
    }

    int getCallbackTimeoutMs() {
        return callbackTimeoutMs;
    }

    boolean isPersistent() {
        return persistent;
    }

    String getUpdateEndpointOrDefault() {
        return hasText(updateEndpoint) ? updateEndpoint : endpoint;
    }

    String getUpdateRegionOrDefault() {
        return hasText(updateRegion) ? updateRegion : region;
    }

    String getUpdateTopicIdOrDefault() {
        return hasText(updateTopicId) ? updateTopicId : topicId;
    }

    private static String normalizeCompress(String value) {
        if (value == null || value.isEmpty()) {
            return "lz4";
        }
        if ("none".equalsIgnoreCase(value)) {
            return "none";
        }
        if ("lz4".equalsIgnoreCase(value)) {
            return "lz4";
        }
        throw new IllegalArgumentException("unsupported compress type: " + value);
    }

    private static int parsePositiveInt(String value, int fallback, String key) {
        if (!hasText(value)) {
            return fallback;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(key + " must be > 0");
        }
        return parsed;
    }

    private static int parseNonNegativeInt(String value, int fallback, String key) {
        if (!hasText(value)) {
            return fallback;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(key + " must be >= 0");
        }
        return parsed;
    }

    private static boolean parseBoolean(String value) {
        return value != null && ("1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value));
    }

    private static String requireText(Map<String, String> values, String key) {
        String value = values.get(key);
        if (!hasText(value)) {
            throw new IllegalArgumentException("missing required config: " + key);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
