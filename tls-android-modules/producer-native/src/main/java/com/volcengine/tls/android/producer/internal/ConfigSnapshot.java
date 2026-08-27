package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.BuildConfig;
import com.volcengine.tls.android.producer.LogProducerConfig;

public final class ConfigSnapshot {
    private final String endpoint;
    private final String region;
    private final String projectId;
    private final String topicId;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String securityToken;
    private final String userAgent;
    private final String hashKey;
    private final String source;
    private final LogProducerConfig.CompressType compressType;
    private final int packetLogBytes;
    private final int packetLogCount;
    private final int packetTimeoutMs;
    private final int maxBufferLimit;
    private final int sendThreadCount;
    private final int retryMaxAttempts;
    private final int retryTotalTimeoutMs;
    private final int retryInitialIntervalMs;
    private final int retryMaxIntervalMs;
    private final boolean persistent;
    private final String persistentFilePath;
    private final boolean persistentForceFlush;
    private final LogProducerConfig.PersistentDurability persistentDurability;
    private final int persistentMaxFileCount;
    private final int persistentMaxFileSize;
    private final int persistentMaxLogCount;
    private final int persistentMaxBytes;
    private final int persistentMaxRecords;
    private final int persistentMaxSegments;
    private final int persistentHighWatermarkPct;
    private final int persistentLowWatermarkPct;
    private final LogProducerConfig.PersistentOverflowPolicy persistentOverflowPolicy;
    private final int persistentSampleEveryN;
    private final int persistentBlockTimeoutMs;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int destroyWaitMs;
    private final int destroyFlusherWaitMs;
    private final int destroySenderWaitMs;
    private final boolean destroyWaitSplitConfigured;
    private final boolean callbackFromSenderThread;
    private final boolean enableTimeNs;
    private final String[] tagKeys;
    private final String[] tagValues;

    public ConfigSnapshot(LogProducerConfig sourceConfig, String processName) {
        if (sourceConfig == null) {
            this.endpoint = null;
            this.region = null;
            this.projectId = null;
            this.topicId = null;
            this.accessKeyId = null;
            this.accessKeySecret = null;
            this.securityToken = null;
            this.userAgent = null;
            this.hashKey = null;
            this.source = null;
            this.compressType = null;
            this.packetLogBytes = 0;
            this.packetLogCount = 0;
            this.packetTimeoutMs = 0;
            this.maxBufferLimit = 0;
            this.retryMaxAttempts = 0;
            this.retryTotalTimeoutMs = 0;
            this.retryInitialIntervalMs = 0;
            this.retryMaxIntervalMs = 0;
            this.sendThreadCount = 0;
            this.persistent = false;
            this.persistentFilePath = null;
            this.persistentForceFlush = false;
            this.persistentDurability = LogProducerConfig.PersistentDurability.BUFFERED_WAL;
            this.persistentMaxFileCount = 0;
            this.persistentMaxFileSize = 0;
            this.persistentMaxLogCount = 0;
            this.persistentMaxBytes = 0;
            this.persistentMaxRecords = 0;
            this.persistentMaxSegments = 0;
            this.persistentHighWatermarkPct = 85;
            this.persistentLowWatermarkPct = 70;
            this.persistentOverflowPolicy = LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW;
            this.persistentSampleEveryN = 10;
            this.persistentBlockTimeoutMs = 1000;
            this.connectTimeoutMs = 0;
            this.requestTimeoutMs = 0;
            this.destroyWaitMs = 0;
            this.destroyFlusherWaitMs = 0;
            this.destroySenderWaitMs = 0;
            this.destroyWaitSplitConfigured = false;
            this.callbackFromSenderThread = false;
            this.enableTimeNs = false;
            this.tagKeys = new String[0];
            this.tagValues = new String[0];
            return;
        }

        this.endpoint = sourceConfig.getEndpoint();
        this.region = sourceConfig.getRegion();
        this.projectId = sourceConfig.getProjectId();
        this.topicId = sourceConfig.getTopicId();
        this.accessKeyId = sourceConfig.getAccessKeyId();
        this.accessKeySecret = sourceConfig.getAccessKeySecret();
        this.securityToken = sourceConfig.getSecurityToken();
        this.userAgent = BuildConfig.SDK_USER_AGENT;
        this.hashKey = sourceConfig.getHashKey();
        this.source = sourceConfig.getSource();
        this.compressType = sourceConfig.getCompressType();
        this.packetLogBytes = sourceConfig.getPacketLogBytes();
        this.packetLogCount = sourceConfig.getPacketLogCount();
        this.packetTimeoutMs = sourceConfig.getPacketTimeoutMs();
        this.maxBufferLimit = sourceConfig.getMaxBufferLimit();
        this.retryMaxAttempts = sourceConfig.getRetryMaxAttempts();
        this.retryTotalTimeoutMs = sourceConfig.getRetryTotalTimeoutMs();
        this.retryInitialIntervalMs = sourceConfig.getRetryInitialIntervalMs();
        this.retryMaxIntervalMs = sourceConfig.getRetryMaxIntervalMs();
        this.persistent = sourceConfig.isPersistent();
        this.persistentFilePath = this.persistent
                ? ProcessUtil.rewritePersistentPath(sourceConfig.getPersistentFilePath(), processName)
                : sourceConfig.getPersistentFilePath();
        this.persistentForceFlush = sourceConfig.isPersistentForceFlush();
        this.persistentDurability = sourceConfig.getPersistentDurability();
        this.persistentMaxFileCount = sourceConfig.getPersistentMaxFileCount();
        this.persistentMaxFileSize = sourceConfig.getPersistentMaxFileSize();
        this.persistentMaxLogCount = sourceConfig.getPersistentMaxLogCount();
        this.persistentMaxBytes = sourceConfig.getPersistentMaxBytes();
        this.persistentMaxRecords = sourceConfig.getPersistentMaxRecords();
        this.persistentMaxSegments = sourceConfig.getPersistentMaxSegments();
        this.persistentHighWatermarkPct = sourceConfig.getPersistentHighWatermarkPct();
        this.persistentLowWatermarkPct = sourceConfig.getPersistentLowWatermarkPct();
        this.persistentOverflowPolicy = sourceConfig.getPersistentOverflowPolicy();
        this.persistentSampleEveryN = sourceConfig.getPersistentSampleEveryN();
        this.persistentBlockTimeoutMs = sourceConfig.getPersistentBlockTimeoutMs();
        this.connectTimeoutMs = sourceConfig.getConnectTimeoutMs();
        this.requestTimeoutMs = sourceConfig.getRequestTimeoutMs();
        this.destroyWaitMs = sourceConfig.isDestroyWaitSplitConfigured() ? 0 : sourceConfig.getDestroyWaitMs();
        this.destroyFlusherWaitMs = sourceConfig.getDestroyFlusherWaitMs();
        this.destroySenderWaitMs = sourceConfig.getDestroySenderWaitMs();
        this.destroyWaitSplitConfigured = sourceConfig.isDestroyWaitSplitConfigured();
        this.callbackFromSenderThread = sourceConfig.isCallbackFromSenderThread();
        this.enableTimeNs = sourceConfig.isEnableTimeNs();
        this.sendThreadCount = this.persistent ? 1 : sourceConfig.getSendThreadCount();
        this.tagKeys = new String[sourceConfig.getTagCount()];
        this.tagValues = new String[sourceConfig.getTagCount()];
        for (int i = 0; i < tagKeys.length; i++) {
            tagKeys[i] = sourceConfig.getTagKey(i);
            tagValues[i] = sourceConfig.getTagValue(i);
        }
    }

    public int getSendThreadCount() {
        return sendThreadCount;
    }

    public int getDestroyWaitMs() {
        return destroyWaitMs;
    }

    public int getDestroyFlusherWaitMs() {
        return destroyFlusherWaitMs;
    }

    public int getDestroySenderWaitMs() {
        return destroySenderWaitMs;
    }

    public boolean isDestroyWaitSplitConfigured() {
        return destroyWaitSplitConfigured;
    }

    public String getPersistentFilePath() {
        return persistentFilePath;
    }

    public int getTagCount() {
        return tagKeys.length;
    }

    public String getTagKey(int index) {
        return tagKeys[index];
    }

    public String getTagValue(int index) {
        return tagValues[index];
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRegion() {
        return region;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTopicId() {
        return topicId;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getHashKey() {
        return hashKey;
    }

    public String getSource() {
        return source;
    }

    public LogProducerConfig.CompressType getCompressType() {
        return compressType;
    }

    public int getPacketLogBytes() {
        return packetLogBytes;
    }

    public int getPacketLogCount() {
        return packetLogCount;
    }

    public int getPacketTimeoutMs() {
        return packetTimeoutMs;
    }

    public int getMaxBufferLimit() {
        return maxBufferLimit;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public int getRetryTotalTimeoutMs() {
        return retryTotalTimeoutMs;
    }

    public int getRetryInitialIntervalMs() {
        return retryInitialIntervalMs;
    }

    public int getRetryMaxIntervalMs() {
        return retryMaxIntervalMs;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean isPersistentForceFlush() {
        return persistentForceFlush;
    }

    public LogProducerConfig.PersistentDurability getPersistentDurability() {
        return persistentDurability;
    }

    public int getPersistentMaxFileCount() {
        return persistentMaxFileCount;
    }

    public int getPersistentMaxFileSize() {
        return persistentMaxFileSize;
    }

    public int getPersistentMaxLogCount() {
        return persistentMaxLogCount;
    }

    public int getPersistentMaxBytes() {
        return persistentMaxBytes;
    }

    public int getPersistentMaxRecords() {
        return persistentMaxRecords;
    }

    public int getPersistentMaxSegments() {
        return persistentMaxSegments;
    }

    public int getPersistentHighWatermarkPct() {
        return persistentHighWatermarkPct;
    }

    public int getPersistentLowWatermarkPct() {
        return persistentLowWatermarkPct;
    }

    public LogProducerConfig.PersistentOverflowPolicy getPersistentOverflowPolicy() {
        return persistentOverflowPolicy;
    }

    public int getPersistentSampleEveryN() {
        return persistentSampleEveryN;
    }

    public int getPersistentBlockTimeoutMs() {
        return persistentBlockTimeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public boolean isCallbackFromSenderThread() {
        return callbackFromSenderThread;
    }

    public boolean isEnableTimeNs() {
        return enableTimeNs;
    }

    public void validateForCreate() {
        requireNonBlank(endpoint, "endpoint is required");
        requireNonBlank(region, "region is required");
        requireNonBlank(topicId, "topicId is required");
        if (sendThreadCount <= 0) {
            throw new IllegalArgumentException("sendThreadCount must be > 0");
        }
        if (retryTotalTimeoutMs <= 0) {
            throw new IllegalArgumentException("retryTotalTimeoutMs must be > 0");
        }
        if (retryMaxIntervalMs < retryInitialIntervalMs) {
            throw new IllegalArgumentException("retryMaxIntervalMs must be >= retryInitialIntervalMs");
        }
        if (persistentMaxBytes < 0) {
            throw new IllegalArgumentException("persistentMaxBytes must be >= 0");
        }
        if (persistentMaxRecords < 0) {
            throw new IllegalArgumentException("persistentMaxRecords must be >= 0");
        }
        if (persistentMaxSegments < 0) {
            throw new IllegalArgumentException("persistentMaxSegments must be >= 0");
        }
        requireWatermark("persistentHighWatermarkPct", persistentHighWatermarkPct);
        requireWatermark("persistentLowWatermarkPct", persistentLowWatermarkPct);
        if (persistentOverflowPolicy == null) {
            throw new IllegalArgumentException("persistentOverflowPolicy == null");
        }
        if (persistentSampleEveryN <= 0) {
            throw new IllegalArgumentException("persistentSampleEveryN must be > 0");
        }
        if (persistentBlockTimeoutMs <= 0) {
            throw new IllegalArgumentException("persistentBlockTimeoutMs must be > 0");
        }
        if (persistent) {
            if (isBlank(persistentFilePath)) {
                throw new IllegalArgumentException("persistent mode requires persistentFilePath");
            }
            if (persistentMaxFileCount <= 0) {
                throw new IllegalArgumentException("persistentMaxFileCount must be > 0 in persistent mode");
            }
            if (persistentMaxFileSize <= 0) {
                throw new IllegalArgumentException("persistentMaxFileSize must be > 0 in persistent mode");
            }
            if (persistentMaxLogCount <= 0) {
                throw new IllegalArgumentException("persistentMaxLogCount must be > 0 in persistent mode");
            }
            if (persistentLowWatermarkPct >= persistentHighWatermarkPct) {
                throw new IllegalArgumentException(
                        "persistentLowWatermarkPct must be < persistentHighWatermarkPct in persistent mode");
            }
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireWatermark(String name, int value) {
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException(name + " must be in [1, 100]");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
