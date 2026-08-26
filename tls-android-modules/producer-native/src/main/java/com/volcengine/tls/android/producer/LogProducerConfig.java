package com.volcengine.tls.android.producer;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class LogProducerConfig {
    public enum CompressType {
        NONE,
        LZ4
    }

    public enum PersistentDurability {
        BUFFERED_WAL,
        SYNC_WAL
    }

    private static final int RETRY_MAX_ATTEMPTS_MIN = 0;
    private static final int RETRY_MAX_ATTEMPTS_MAX = 50;
    private static final int RETRY_TOTAL_TIMEOUT_MS_DEFAULT = 90 * 1000;
    private static final int RETRY_INITIAL_INTERVAL_MS_DEFAULT = 500;
    private static final int RETRY_MAX_INTERVAL_MS_DEFAULT = 10 * 1000;
    private static final int RETRY_INITIAL_INTERVAL_MS_MIN = 100;
    private static final int RETRY_INITIAL_INTERVAL_MS_MAX = 30 * 1000;
    private static final int RETRY_MAX_INTERVAL_MS_MIN = 1000;
    private static final int RETRY_MAX_INTERVAL_MS_MAX = 60 * 1000;

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
    private int retryMaxAttempts;
    private int retryTotalTimeoutMs = RETRY_TOTAL_TIMEOUT_MS_DEFAULT;
    private int retryInitialIntervalMs = RETRY_INITIAL_INTERVAL_MS_DEFAULT;
    private int retryMaxIntervalMs = RETRY_MAX_INTERVAL_MS_DEFAULT;
    private boolean persistent;
    private String persistentFilePath;
    private boolean persistentForceFlush;
    private PersistentDurability persistentDurability = PersistentDurability.BUFFERED_WAL;
    private boolean persistentDurabilityConfigured;
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
    private final List<String> tagKeys = new ArrayList<>();
    private final List<String> tagValues = new ArrayList<>();
    private boolean frozen;

    public LogProducerConfig() {
    }

    /**
     * Compatibility-only constructor. It does not derive a default persistent path from
     * {@link Context}; call {@link #setPersistentFilePath(String)} explicitly before enabling
     * persistent mode.
     */
    @Deprecated
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

    /**
     * Compatibility-only constructor. It does not derive a default persistent path from
     * {@link Context}; call {@link #setPersistentFilePath(String)} explicitly before enabling
     * persistent mode.
     */
    @Deprecated
    public LogProducerConfig(Context context, String endpoint, String region, String projectId, String topicId) {
        this(endpoint, region, projectId, topicId);
    }

    /**
     * Compatibility-only constructor. It does not derive a default persistent path from
     * {@link Context}; call {@link #setPersistentFilePath(String)} explicitly before enabling
     * persistent mode.
     */
    @Deprecated
    public LogProducerConfig(Context context, String endpoint, String region, String projectId, String topicId, String accessKeyId, String accessKeySecret) {
        this(endpoint, region, projectId, topicId, accessKeyId, accessKeySecret);
    }

    /**
     * Compatibility-only constructor. It does not derive a default persistent path from
     * {@link Context}; call {@link #setPersistentFilePath(String)} explicitly before enabling
     * persistent mode.
     */
    @Deprecated
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
        if (compressType == null) {
            throw new NullPointerException("compressType == null");
        }
        this.compressType = compressType;
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

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public LogProducerConfig setRetryMaxAttempts(int retryMaxAttempts) {
        ensureMutable();
        if (retryMaxAttempts < RETRY_MAX_ATTEMPTS_MIN || retryMaxAttempts > RETRY_MAX_ATTEMPTS_MAX) {
            throw new IllegalArgumentException("retryMaxAttempts must be in [0, 50]");
        }
        this.retryMaxAttempts = retryMaxAttempts;
        return this;
    }

    public int getRetryTotalTimeoutMs() {
        return retryTotalTimeoutMs;
    }

    public LogProducerConfig setRetryTotalTimeoutMs(int retryTotalTimeoutMs) {
        ensureMutable();
        if (retryTotalTimeoutMs <= 0) {
            throw new IllegalArgumentException("retryTotalTimeoutMs must be > 0");
        }
        this.retryTotalTimeoutMs = retryTotalTimeoutMs;
        return this;
    }

    public int getRetryInitialIntervalMs() {
        return retryInitialIntervalMs;
    }

    public LogProducerConfig setRetryInitialIntervalMs(int retryInitialIntervalMs) {
        ensureMutable();
        if (retryInitialIntervalMs < RETRY_INITIAL_INTERVAL_MS_MIN
                || retryInitialIntervalMs > RETRY_INITIAL_INTERVAL_MS_MAX) {
            throw new IllegalArgumentException("retryInitialIntervalMs must be in [100, 30000]");
        }
        this.retryInitialIntervalMs = retryInitialIntervalMs;
        return this;
    }

    public int getRetryMaxIntervalMs() {
        return retryMaxIntervalMs;
    }

    public LogProducerConfig setRetryMaxIntervalMs(int retryMaxIntervalMs) {
        ensureMutable();
        if (retryMaxIntervalMs < RETRY_MAX_INTERVAL_MS_MIN
                || retryMaxIntervalMs > RETRY_MAX_INTERVAL_MS_MAX) {
            throw new IllegalArgumentException("retryMaxIntervalMs must be in [1000, 60000]");
        }
        this.retryMaxIntervalMs = retryMaxIntervalMs;
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

    /**
     * Sets the directory used for local persistent files.
     * Reusing one persistent path across restarts is allowed, but callers should keep
     * {@code endpoint/region/topicId} stable for that path. If the send target changes,
     * switch to a new persistent path; otherwise backlog recovered from the old path may
     * be sent to the new target.
     */
    public LogProducerConfig setPersistentFilePath(String persistentFilePath) {
        ensureMutable();
        this.persistentFilePath = persistentFilePath;
        return this;
    }

    public boolean isPersistentForceFlush() {
        return persistentForceFlush;
    }

    /**
     * Compatibility API. {@code true} maps to {@link PersistentDurability#SYNC_WAL};
     * {@code false} maps to {@link PersistentDurability#BUFFERED_WAL} unless durability was
     * explicitly configured with {@link #setPersistentDurability(PersistentDurability)}.
     */
    @Deprecated
    public LogProducerConfig setPersistentForceFlush(boolean persistentForceFlush) {
        ensureMutable();
        if (persistentForceFlush && persistentDurabilityConfigured
                && persistentDurability == PersistentDurability.BUFFERED_WAL) {
            throw new IllegalArgumentException(
                    "persistentForceFlush=true conflicts with BUFFERED_WAL durability");
        }
        this.persistentForceFlush = persistentForceFlush;
        if (!persistentDurabilityConfigured) {
            this.persistentDurability = persistentForceFlush
                    ? PersistentDurability.SYNC_WAL
                    : PersistentDurability.BUFFERED_WAL;
        }
        return this;
    }

    public PersistentDurability getPersistentDurability() {
        return persistentDurability;
    }

    /**
     * Controls when an accepted persistent record becomes durable outside the process.
     * Buffered WAL syncs on rotation, flush, and close; sync WAL syncs every append.
     */
    public LogProducerConfig setPersistentDurability(PersistentDurability persistentDurability) {
        ensureMutable();
        if (persistentDurability == null) {
            throw new IllegalArgumentException("persistentDurability == null");
        }
        if (persistentForceFlush && persistentDurability == PersistentDurability.BUFFERED_WAL) {
            throw new IllegalArgumentException(
                    "BUFFERED_WAL durability conflicts with persistentForceFlush=true");
        }
        this.persistentDurability = persistentDurability;
        this.persistentDurabilityConfigured = true;
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
        if (key == null || value == null) {
            throw new IllegalArgumentException("tag key and value cannot be null");
        }
        tagKeys.add(key);
        tagValues.add(value);
        return this;
    }

    public int getTagCount() {
        return tagKeys.size();
    }

    public String getTagKey(int index) {
        return tagKeys.get(index);
    }

    public String getTagValue(int index) {
        return tagValues.get(index);
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

    void validateForCreate(String processName) {
        requireNonBlank(endpoint, "endpoint is required");
        requireNonBlank(region, "region is required");
        requireNonBlank(topicId, "topicId is required");
        if (sendThreadCount <= 0) {
            throw new IllegalArgumentException("sendThreadCount must be > 0");
        }
        if (retryTotalTimeoutMs <= 0) {
            throw new IllegalArgumentException("retryTotalTimeoutMs must be > 0");
        }
        if (retryInitialIntervalMs < RETRY_INITIAL_INTERVAL_MS_MIN
                || retryInitialIntervalMs > RETRY_INITIAL_INTERVAL_MS_MAX) {
            throw new IllegalArgumentException("retryInitialIntervalMs must be in [100, 30000]");
        }
        if (retryMaxIntervalMs < RETRY_MAX_INTERVAL_MS_MIN
                || retryMaxIntervalMs > RETRY_MAX_INTERVAL_MS_MAX) {
            throw new IllegalArgumentException("retryMaxIntervalMs must be in [1000, 60000]");
        }
        if (retryMaxAttempts < RETRY_MAX_ATTEMPTS_MIN || retryMaxAttempts > RETRY_MAX_ATTEMPTS_MAX) {
            throw new IllegalArgumentException("retryMaxAttempts must be in [0, 50]");
        }
        if (retryMaxIntervalMs < retryInitialIntervalMs) {
            throw new IllegalArgumentException("retryMaxIntervalMs must be >= retryInitialIntervalMs");
        }
        if (persistent) {
            requireNonBlank(persistentFilePath, "persistent mode requires persistentFilePath");
            if (isBlank(processName)) {
                throw new IllegalStateException("persistent mode requires resolvable process identity");
            }
        }
    }

    public boolean isValid() {
        return !isBlank(endpoint)
                && !isBlank(region)
                && !isBlank(topicId)
                && sendThreadCount > 0
                && retryTotalTimeoutMs > 0
                && retryInitialIntervalMs >= RETRY_INITIAL_INTERVAL_MS_MIN
                && retryInitialIntervalMs <= RETRY_INITIAL_INTERVAL_MS_MAX
                && retryMaxIntervalMs >= RETRY_MAX_INTERVAL_MS_MIN
                && retryMaxIntervalMs <= RETRY_MAX_INTERVAL_MS_MAX
                && retryMaxIntervalMs >= retryInitialIntervalMs
                && retryMaxAttempts >= RETRY_MAX_ATTEMPTS_MIN
                && retryMaxAttempts <= RETRY_MAX_ATTEMPTS_MAX
                && (!persistent || !isBlank(persistentFilePath));
    }

    public boolean isEnabled() {
        return isValid();
    }

    private static void requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
