package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.LogProducerConfig;

public final class ConfigSnapshot {
    private final String endpoint;
    private final String region;
    private final String projectId;
    private final String topicId;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String securityToken;
    private final String hashKey;
    private final String source;
    private final LogProducerConfig.CompressType compressType;
    private final int packetLogBytes;
    private final int packetLogCount;
    private final int packetTimeoutMs;
    private final int maxBufferLimit;
    private final int sendThreadCount;
    private final int retryCount;
    private final boolean persistent;
    private final String persistentFilePath;
    private final boolean persistentForceFlush;
    private final int persistentMaxFileCount;
    private final int persistentMaxFileSize;
    private final int persistentMaxLogCount;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int destroyWaitMs;
    private final int destroyFlusherWaitMs;
    private final int destroySenderWaitMs;
    private final boolean destroyWaitSplitConfigured;
    private final boolean callbackFromSenderThread;
    private final boolean enableTimeNs;
    private final boolean hasSourceConfig;

    public ConfigSnapshot(LogProducerConfig sourceConfig, String processName) {
        if (sourceConfig == null) {
            this.hasSourceConfig = false;
            this.endpoint = null;
            this.region = null;
            this.projectId = null;
            this.topicId = null;
            this.accessKeyId = null;
            this.accessKeySecret = null;
            this.securityToken = null;
            this.hashKey = null;
            this.source = null;
            this.compressType = null;
            this.packetLogBytes = 0;
            this.packetLogCount = 0;
            this.packetTimeoutMs = 0;
            this.maxBufferLimit = 0;
            this.retryCount = 0;
            this.sendThreadCount = 0;
            this.persistent = false;
            this.persistentFilePath = null;
            this.persistentForceFlush = false;
            this.persistentMaxFileCount = 0;
            this.persistentMaxFileSize = 0;
            this.persistentMaxLogCount = 0;
            this.connectTimeoutMs = 0;
            this.requestTimeoutMs = 0;
            this.destroyWaitMs = 0;
            this.destroyFlusherWaitMs = 0;
            this.destroySenderWaitMs = 0;
            this.destroyWaitSplitConfigured = false;
            this.callbackFromSenderThread = false;
            this.enableTimeNs = false;
            return;
        }

        this.hasSourceConfig = true;
        this.endpoint = sourceConfig.getEndpoint();
        this.region = sourceConfig.getRegion();
        this.projectId = sourceConfig.getProjectId();
        this.topicId = sourceConfig.getTopicId();
        this.accessKeyId = sourceConfig.getAccessKeyId();
        this.accessKeySecret = sourceConfig.getAccessKeySecret();
        this.securityToken = sourceConfig.getSecurityToken();
        this.hashKey = sourceConfig.getHashKey();
        this.source = sourceConfig.getSource();
        this.compressType = sourceConfig.getCompressType();
        this.packetLogBytes = sourceConfig.getPacketLogBytes();
        this.packetLogCount = sourceConfig.getPacketLogCount();
        this.packetTimeoutMs = sourceConfig.getPacketTimeoutMs();
        this.maxBufferLimit = sourceConfig.getMaxBufferLimit();
        this.persistent = sourceConfig.isPersistent();
        this.persistentFilePath = this.persistent
                ? ProcessUtil.rewritePersistentPath(sourceConfig.getPersistentFilePath(), processName)
                : sourceConfig.getPersistentFilePath();
        this.persistentForceFlush = sourceConfig.isPersistentForceFlush();
        this.persistentMaxFileCount = sourceConfig.getPersistentMaxFileCount();
        this.persistentMaxFileSize = sourceConfig.getPersistentMaxFileSize();
        this.persistentMaxLogCount = sourceConfig.getPersistentMaxLogCount();
        this.retryCount = sourceConfig.getRetryCount();
        this.connectTimeoutMs = sourceConfig.getConnectTimeoutMs();
        this.requestTimeoutMs = sourceConfig.getRequestTimeoutMs();
        this.destroyWaitMs = sourceConfig.isDestroyWaitSplitConfigured() ? 0 : sourceConfig.getDestroyWaitMs();
        this.destroyFlusherWaitMs = sourceConfig.getDestroyFlusherWaitMs();
        this.destroySenderWaitMs = sourceConfig.getDestroySenderWaitMs();
        this.destroyWaitSplitConfigured = sourceConfig.isDestroyWaitSplitConfigured();
        this.callbackFromSenderThread = sourceConfig.isCallbackFromSenderThread();
        this.enableTimeNs = sourceConfig.isEnableTimeNs();
        this.sendThreadCount = this.persistent ? 1 : sourceConfig.getSendThreadCount();
    }

    public LogProducerConfig toConfig() {
        if (!hasSourceConfig) {
            return null;
        }

        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(endpoint)
                .setRegion(region)
                .setProjectId(projectId)
                .setTopicId(topicId)
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setSecurityToken(securityToken)
                .setHashKey(hashKey)
                .setSource(source)
                .setCompressType(compressType)
                .setPacketLogBytes(packetLogBytes)
                .setPacketLogCount(packetLogCount)
                .setPacketTimeoutMs(packetTimeoutMs)
                .setMaxBufferLimit(maxBufferLimit)
                .setSendThreadCount(sendThreadCount)
                .setRetryCount(retryCount)
                .setPersistent(persistent)
                .setPersistentFilePath(persistentFilePath)
                .setPersistentForceFlush(persistentForceFlush)
                .setPersistentMaxFileCount(persistentMaxFileCount)
                .setPersistentMaxFileSize(persistentMaxFileSize)
                .setPersistentMaxLogCount(persistentMaxLogCount)
                .setConnectTimeoutMs(connectTimeoutMs)
                .setRequestTimeoutMs(requestTimeoutMs)
                .setCallbackFromSenderThread(callbackFromSenderThread)
                .setEnableTimeNs(enableTimeNs);
        if (destroyWaitSplitConfigured) {
            config.setDestroyFlusherWaitMs(destroyFlusherWaitMs);
            config.setDestroySenderWaitMs(destroySenderWaitMs);
        } else {
            config.setDestroyWaitMs(destroyWaitMs);
        }
        return config;
    }

    public int getSendThreadCount() {
        return sendThreadCount;
    }

    public int getDestroyWaitMs() {
        if (destroyWaitSplitConfigured) {
            return destroyFlusherWaitMs + destroySenderWaitMs;
        }
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
}
