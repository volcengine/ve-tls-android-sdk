package com.volcengine.tls.android.producer;

import android.content.Context;

public final class LogProducerConfig {
    public enum CompressType { NONE, LZ4 }

    private String endpoint;
    private String region;
    private String topicId;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private CompressType compressType = CompressType.LZ4;
    private int packetLogBytes = 1024 * 1024;
    private int packetLogCount = 1024;
    private int packetTimeoutMs = 3000;
    private int sendThreadCount = 1;
    private int retryMaxAttempts = 0;
    private int retryTotalTimeoutMs = 90 * 1000;
    private int retryInitialIntervalMs = 500;
    private int retryMaxIntervalMs = 10 * 1000;

    public LogProducerConfig() {}
    public LogProducerConfig(Context context) { this(); }

    public String getEndpoint() { return endpoint; }
    public LogProducerConfig setEndpoint(String endpoint) { this.endpoint = endpoint; return this; }
    public String getRegion() { return region; }
    public LogProducerConfig setRegion(String region) { this.region = region; return this; }
    public String getTopicId() { return topicId; }
    public LogProducerConfig setTopicId(String topicId) { this.topicId = topicId; return this; }
    public String getAccessKeyId() { return accessKeyId; }
    public LogProducerConfig setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; return this; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public LogProducerConfig setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; return this; }
    public String getSecurityToken() { return securityToken; }
    public LogProducerConfig setSecurityToken(String securityToken) { this.securityToken = securityToken; return this; }
    public CompressType getCompressType() { return compressType; }
    public LogProducerConfig setCompressType(CompressType compressType) {
        if (compressType == null) {
            throw new NullPointerException("compressType == null");
        }
        this.compressType = compressType;
        return this;
    }
    public int getPacketLogBytes() { return packetLogBytes; }
    public LogProducerConfig setPacketLogBytes(int packetLogBytes) { this.packetLogBytes = packetLogBytes; return this; }
    public int getPacketLogCount() { return packetLogCount; }
    public LogProducerConfig setPacketLogCount(int packetLogCount) { this.packetLogCount = packetLogCount; return this; }
    public int getPacketTimeoutMs() { return packetTimeoutMs; }
    public LogProducerConfig setPacketTimeoutMs(int packetTimeoutMs) { this.packetTimeoutMs = packetTimeoutMs; return this; }
    public int getSendThreadCount() { return sendThreadCount; }
    public LogProducerConfig setSendThreadCount(int sendThreadCount) { this.sendThreadCount = sendThreadCount; return this; }
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public LogProducerConfig setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; return this; }
    public int getRetryTotalTimeoutMs() { return retryTotalTimeoutMs; }
    public LogProducerConfig setRetryTotalTimeoutMs(int retryTotalTimeoutMs) { this.retryTotalTimeoutMs = retryTotalTimeoutMs; return this; }
    public int getRetryInitialIntervalMs() { return retryInitialIntervalMs; }
    public LogProducerConfig setRetryInitialIntervalMs(int retryInitialIntervalMs) { this.retryInitialIntervalMs = retryInitialIntervalMs; return this; }
    public int getRetryMaxIntervalMs() { return retryMaxIntervalMs; }
    public LogProducerConfig setRetryMaxIntervalMs(int retryMaxIntervalMs) { this.retryMaxIntervalMs = retryMaxIntervalMs; return this; }
}
