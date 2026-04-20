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
    private int destroyFlusherWaitMs;
    private int destroySenderWaitMs;
    private boolean destroyWaitSplitConfigured;
    private boolean callbackFromSenderThread;
    private boolean enableTimeNs;
    private boolean frozen;

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
        ensureMutable();
        this.endpoint = endpoint;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public LogProducerConfig setRegion(String region) {
        ensureMutable();
        this.region = region;
        return this;
    }

    public String getProjectId() {
        return projectId;
    }

    public LogProducerConfig setProjectId(String projectId) {
        ensureMutable();
        this.projectId = projectId;
        return this;
    }

    public String getTopicId() {
        return topicId;
    }

    public LogProducerConfig setTopicId(String topicId) {
        ensureMutable();
        this.topicId = topicId;
        return this;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public LogProducerConfig setAccessKeyId(String accessKeyId) {
        ensureMutable();
        this.accessKeyId = accessKeyId;
        return this;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public LogProducerConfig setAccessKeySecret(String accessKeySecret) {
        ensureMutable();
        this.accessKeySecret = accessKeySecret;
        return this;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public LogProducerConfig setSecurityToken(String securityToken) {
        ensureMutable();
        this.securityToken = securityToken;
        return this;
    }

    public String getHashKey() {
        return hashKey;
    }

    /**
     * Sets the default shard key used for producer requests. The value is passed through to
     * the native producer directly; there is no separate mode flag that must be enabled first.
     */
    public LogProducerConfig setHashKey(String hashKey) {
        ensureMutable();
        this.hashKey = hashKey;
        return this;
    }

    public String getSource() {
        return source;
    }

    public LogProducerConfig setSource(String source) {
        ensureMutable();
        this.source = source;
        return this;
    }

    public CompressType getCompressType() {
        return compressType;
    }

    public LogProducerConfig setCompressType(CompressType compressType) {
        ensureMutable();
        this.compressType = Objects.requireNonNull(compressType);
        return this;
    }

    LogProducerConfig setCompressType(String compressType) {
        ensureMutable();
        if (compressType == null) {
            return this;
        }
        if ("none".equalsIgnoreCase(compressType)) {
            this.compressType = CompressType.NONE;
            return this;
        }
        if ("lz4".equalsIgnoreCase(compressType)) {
            this.compressType = CompressType.LZ4;
            return this;
        }
        throw new IllegalArgumentException("unsupported compress type: " + compressType);
    }

    public int getPacketLogBytes() {
        return packetLogBytes;
    }

    public LogProducerConfig setPacketLogBytes(int packetLogBytes) {
        ensureMutable();
        this.packetLogBytes = packetLogBytes;
        return this;
    }

    public int getPacketLogCount() {
        return packetLogCount;
    }

    public LogProducerConfig setPacketLogCount(int packetLogCount) {
        ensureMutable();
        this.packetLogCount = packetLogCount;
        return this;
    }

    public int getPacketTimeoutMs() {
        return packetTimeoutMs;
    }

    public LogProducerConfig setPacketTimeoutMs(int packetTimeoutMs) {
        ensureMutable();
        this.packetTimeoutMs = packetTimeoutMs;
        return this;
    }

    LogProducerConfig setPacketTimeout(int packetTimeoutMs) {
        ensureMutable();
        this.packetTimeoutMs = packetTimeoutMs;
        return this;
    }

    public int getMaxBufferLimit() {
        return maxBufferLimit;
    }

    public LogProducerConfig setMaxBufferLimit(int maxBufferLimit) {
        ensureMutable();
        this.maxBufferLimit = maxBufferLimit;
        return this;
    }

    public int getSendThreadCount() {
        return sendThreadCount;
    }

    public LogProducerConfig setSendThreadCount(int sendThreadCount) {
        ensureMutable();
        this.sendThreadCount = sendThreadCount;
        return this;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LogProducerConfig setRetryCount(int retryCount) {
        ensureMutable();
        this.retryCount = retryCount;
        return this;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public LogProducerConfig setPersistent(boolean persistent) {
        ensureMutable();
        this.persistent = persistent;
        return this;
    }

    public String getPersistentFilePath() {
        return persistentFilePath;
    }

    public LogProducerConfig setPersistentFilePath(String persistentFilePath) {
        ensureMutable();
        this.persistentFilePath = persistentFilePath;
        return this;
    }

    public boolean isPersistentForceFlush() {
        return persistentForceFlush;
    }

    public LogProducerConfig setPersistentForceFlush(boolean persistentForceFlush) {
        ensureMutable();
        this.persistentForceFlush = persistentForceFlush;
        return this;
    }

    public int getPersistentMaxFileCount() {
        return persistentMaxFileCount;
    }

    public LogProducerConfig setPersistentMaxFileCount(int persistentMaxFileCount) {
        ensureMutable();
        this.persistentMaxFileCount = persistentMaxFileCount;
        return this;
    }

    public int getPersistentMaxFileSize() {
        return persistentMaxFileSize;
    }

    public LogProducerConfig setPersistentMaxFileSize(int persistentMaxFileSize) {
        ensureMutable();
        this.persistentMaxFileSize = persistentMaxFileSize;
        return this;
    }

    public int getPersistentMaxLogCount() {
        return persistentMaxLogCount;
    }

    public LogProducerConfig setPersistentMaxLogCount(int persistentMaxLogCount) {
        ensureMutable();
        this.persistentMaxLogCount = persistentMaxLogCount;
        return this;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public LogProducerConfig setConnectTimeoutMs(int connectTimeoutMs) {
        ensureMutable();
        this.connectTimeoutMs = connectTimeoutMs;
        return this;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public LogProducerConfig setRequestTimeoutMs(int requestTimeoutMs) {
        ensureMutable();
        this.requestTimeoutMs = requestTimeoutMs;
        return this;
    }

    public int getDestroyWaitMs() {
        if (destroyWaitSplitConfigured) {
            return destroyFlusherWaitMs + destroySenderWaitMs;
        }
        return destroyWaitMs;
    }

    public LogProducerConfig setDestroyWaitMs(int destroyWaitMs) {
        ensureMutable();
        this.destroyWaitMs = destroyWaitMs;
        this.destroyFlusherWaitMs = 0;
        this.destroySenderWaitMs = 0;
        this.destroyWaitSplitConfigured = false;
        return this;
    }

    public int getDestroyFlusherWaitMs() {
        return destroyFlusherWaitMs;
    }

    public LogProducerConfig setDestroyFlusherWaitMs(int destroyFlusherWaitMs) {
        ensureMutable();
        this.destroyFlusherWaitMs = destroyFlusherWaitMs;
        this.destroyWaitSplitConfigured = true;
        return this;
    }

    public int getDestroySenderWaitMs() {
        return destroySenderWaitMs;
    }

    public LogProducerConfig setDestroySenderWaitMs(int destroySenderWaitMs) {
        ensureMutable();
        this.destroySenderWaitMs = destroySenderWaitMs;
        this.destroyWaitSplitConfigured = true;
        return this;
    }

    public boolean isDestroyWaitSplitConfigured() {
        return destroyWaitSplitConfigured;
    }

    public boolean isCallbackFromSenderThread() {
        return callbackFromSenderThread;
    }

    public LogProducerConfig setCallbackFromSenderThread(boolean callbackFromSenderThread) {
        ensureMutable();
        this.callbackFromSenderThread = callbackFromSenderThread;
        return this;
    }

    public boolean isEnableTimeNs() {
        return enableTimeNs;
    }

    public LogProducerConfig setEnableTimeNs(boolean enableTimeNs) {
        ensureMutable();
        this.enableTimeNs = enableTimeNs;
        return this;
    }

    public LogProducerConfig addTag(String key, String value) {
        ensureMutable();
        return this;
    }

    public LogProducerConfig freeze() {
        this.frozen = true;
        return this;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("LogProducerConfig is frozen after client creation");
        }
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
