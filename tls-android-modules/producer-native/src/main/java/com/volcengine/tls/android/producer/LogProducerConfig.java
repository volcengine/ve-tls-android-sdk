package com.volcengine.tls.android.producer;

import android.content.Context;

import java.util.Objects;

public final class LogProducerConfig {
    public enum CompressType {
        NONE,
        LZ4
    }

    private String endpoint;
    private String region;
    private String projectId;
    private String topicId;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String hashKey;
    private String source;
    private CompressType compressType = CompressType.LZ4;
    private int packetLogBytes = 1024 * 1024;
    private int packetLogCount = 1024;
    private int packetTimeoutMs = 3000;
    private int maxBufferLimit = 64 * 1024 * 1024;
    private int sendThreadCount = 1;
    private int retryCount = 3;
    private boolean persistent;
    private String persistentFilePath;
    private boolean persistentForceFlush;
    private int persistentMaxFileCount;
    private int persistentMaxFileSize;
    private int persistentMaxLogCount;
    private int connectTimeoutMs;
    private int requestTimeoutMs;
    private int destroyWaitMs;
    private boolean callbackFromSenderThread;
    private boolean enableTimeNs;

    public LogProducerConfig() {
    }

    public LogProducerConfig(Context context) {
        this();
    }

    public LogProducerConfig(String endpoint, String region, String projectId, String topicId) {
        this();
        this.endpoint = endpoint;
        this.region = region;
        this.projectId = projectId;
        this.topicId = topicId;
    }

    public LogProducerConfig(String endpoint, String region, String projectId, String topicId, String accessKeyId, String accessKeySecret) {
        this(endpoint, region, projectId, topicId);
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    public LogProducerConfig(String endpoint, String region, String projectId, String topicId, String accessKeyId, String accessKeySecret, String securityToken) {
        this(endpoint, region, projectId, topicId, accessKeyId, accessKeySecret);
        this.securityToken = securityToken;
    }

    public LogProducerConfig(Context context, String endpoint, String region, String projectId, String topicId) {
        this(endpoint, region, projectId, topicId);
    }

    public LogProducerConfig(Context context, String endpoint, String region, String projectId, String topicId, String accessKeyId, String accessKeySecret) {
        this(endpoint, region, projectId, topicId, accessKeyId, accessKeySecret);
    }

    public LogProducerConfig(Context context, String endpoint, String region, String projectId, String topicId, String accessKeyId, String accessKeySecret, String securityToken) {
        this(endpoint, region, projectId, topicId, accessKeyId, accessKeySecret, securityToken);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public LogProducerConfig setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public LogProducerConfig setRegion(String region) {
        this.region = region;
        return this;
    }

    public String getProjectId() {
        return projectId;
    }

    public LogProducerConfig setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public String getTopicId() {
        return topicId;
    }

    public LogProducerConfig setTopicId(String topicId) {
        this.topicId = topicId;
        return this;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public LogProducerConfig setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
        return this;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public LogProducerConfig setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
        return this;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public LogProducerConfig setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
        return this;
    }

    public String getHashKey() {
        return hashKey;
    }

    public LogProducerConfig setHashKey(String hashKey) {
        this.hashKey = hashKey;
        return this;
    }

    public String getSource() {
        return source;
    }

    public LogProducerConfig setSource(String source) {
        this.source = source;
        return this;
    }

    public CompressType getCompressType() {
        return compressType;
    }

    public LogProducerConfig setCompressType(CompressType compressType) {
        this.compressType = Objects.requireNonNull(compressType);
        return this;
    }

    LogProducerConfig setCompressType(String compressType) {
        if (compressType == null) {
            return this;
        }
        if ("none".equalsIgnoreCase(compressType)) {
            this.compressType = CompressType.NONE;
        } else {
            this.compressType = CompressType.LZ4;
        }
        return this;
    }

    public int getPacketLogBytes() {
        return packetLogBytes;
    }

    public LogProducerConfig setPacketLogBytes(int packetLogBytes) {
        this.packetLogBytes = packetLogBytes;
        return this;
    }

    public int getPacketLogCount() {
        return packetLogCount;
    }

    public LogProducerConfig setPacketLogCount(int packetLogCount) {
        this.packetLogCount = packetLogCount;
        return this;
    }

    public int getPacketTimeoutMs() {
        return packetTimeoutMs;
    }

    public LogProducerConfig setPacketTimeoutMs(int packetTimeoutMs) {
        this.packetTimeoutMs = packetTimeoutMs;
        return this;
    }

    LogProducerConfig setPacketTimeout(int packetTimeoutMs) {
        this.packetTimeoutMs = packetTimeoutMs;
        return this;
    }

    public int getMaxBufferLimit() {
        return maxBufferLimit;
    }

    public LogProducerConfig setMaxBufferLimit(int maxBufferLimit) {
        this.maxBufferLimit = maxBufferLimit;
        return this;
    }

    public int getSendThreadCount() {
        return sendThreadCount;
    }

    public LogProducerConfig setSendThreadCount(int sendThreadCount) {
        this.sendThreadCount = sendThreadCount;
        return this;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LogProducerConfig setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public LogProducerConfig setPersistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    public String getPersistentFilePath() {
        return persistentFilePath;
    }

    public LogProducerConfig setPersistentFilePath(String persistentFilePath) {
        this.persistentFilePath = persistentFilePath;
        return this;
    }

    public boolean isPersistentForceFlush() {
        return persistentForceFlush;
    }

    public LogProducerConfig setPersistentForceFlush(boolean persistentForceFlush) {
        this.persistentForceFlush = persistentForceFlush;
        return this;
    }

    public int getPersistentMaxFileCount() {
        return persistentMaxFileCount;
    }

    public LogProducerConfig setPersistentMaxFileCount(int persistentMaxFileCount) {
        this.persistentMaxFileCount = persistentMaxFileCount;
        return this;
    }

    public int getPersistentMaxFileSize() {
        return persistentMaxFileSize;
    }

    public LogProducerConfig setPersistentMaxFileSize(int persistentMaxFileSize) {
        this.persistentMaxFileSize = persistentMaxFileSize;
        return this;
    }

    public int getPersistentMaxLogCount() {
        return persistentMaxLogCount;
    }

    public LogProducerConfig setPersistentMaxLogCount(int persistentMaxLogCount) {
        this.persistentMaxLogCount = persistentMaxLogCount;
        return this;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public LogProducerConfig setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        return this;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public LogProducerConfig setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
        return this;
    }

    public int getDestroyWaitMs() {
        return destroyWaitMs;
    }

    public LogProducerConfig setDestroyWaitMs(int destroyWaitMs) {
        this.destroyWaitMs = destroyWaitMs;
        return this;
    }

    public boolean isCallbackFromSenderThread() {
        return callbackFromSenderThread;
    }

    public LogProducerConfig setCallbackFromSenderThread(boolean callbackFromSenderThread) {
        this.callbackFromSenderThread = callbackFromSenderThread;
        return this;
    }

    public boolean isEnableTimeNs() {
        return enableTimeNs;
    }

    public LogProducerConfig setEnableTimeNs(boolean enableTimeNs) {
        this.enableTimeNs = enableTimeNs;
        return this;
    }

    public LogProducerConfig addTag(String key, String value) {
        return this;
    }

    public boolean isValid() {
        return endpoint != null && !endpoint.trim().isEmpty() &&
                region != null && !region.trim().isEmpty() &&
                topicId != null && !topicId.trim().isEmpty();
    }

    public boolean isEnabled() {
        return isValid();
    }
}
