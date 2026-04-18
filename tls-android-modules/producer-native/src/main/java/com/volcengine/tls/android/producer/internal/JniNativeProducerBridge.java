package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerConfig;

public final class JniNativeProducerBridge implements NativeProducerBridge {
    private static final boolean NATIVE_LIBRARY_LOADED;
    private static final Throwable NATIVE_LIBRARY_ERROR;

    static {
        boolean loaded = false;
        Throwable error = null;
        try {
            System.loadLibrary("tls_producer_jni");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            error = e;
        }
        NATIVE_LIBRARY_LOADED = loaded;
        NATIVE_LIBRARY_ERROR = error;
    }

    public long create(ConfigSnapshot config, LogProducerCallback callback) {
        LogProducerConfig nativeConfig = config == null ? null : config.toConfig();
        int destroyWaitMs = config == null ? 0 : config.getDestroyWaitMs();
        return createInternal(nativeConfig, destroyWaitMs, callback);
    }

    @Override
    public long create(LogProducerConfig config, LogProducerCallback callback) {
        return createInternal(config, config == null ? 0 : config.getDestroyWaitMs(), callback);
    }

    @Override
    public void updateEndpoint(long producerHandle, String endpoint, String region, String topicId) {
        requireNativeLibrary();
        int result = nativeUpdateEndpoint(producerHandle, endpoint, region, topicId);
        if (result != 0) {
            throw new IllegalStateException("native updateEndpoint failed: " + result);
        }
    }

    @Override
    public void resetSecurityToken(long producerHandle, String accessKeyId, String accessKeySecret, String securityToken) {
        requireNativeLibrary();
        int result = nativeResetSecurityToken(producerHandle, accessKeyId, accessKeySecret, securityToken);
        if (result != 0) {
            throw new IllegalStateException("native resetSecurityToken failed: " + result);
        }
    }

    @Override
    public void addLog(long producerHandle, Log log) {
        addLog(producerHandle, log, 0);
    }

    @Override
    public void addLog(long producerHandle, Log log, int flush) {
        throw new UnsupportedOperationException("addLog JNI bridge is not implemented yet");
    }

    @Override
    public void destroy(long producerHandle, int destroyWaitMs) {
        if (producerHandle == 0) {
            return;
        }
        requireNativeLibrary();
        nativeDestroy(producerHandle, destroyWaitMs);
    }

    @Override
    public void destroyAsync(long producerHandle, int destroyWaitMs) {
        if (producerHandle == 0) {
            return;
        }
        Thread destroyThread = new Thread(
                () -> destroy(producerHandle, destroyWaitMs),
                "tls-producer-destroy");
        destroyThread.setDaemon(true);
        destroyThread.start();
    }

    private long createInternal(LogProducerConfig config, int destroyWaitMs, LogProducerCallback callback) {
        requireNativeLibrary();
        if (config == null) {
            throw new IllegalArgumentException("config == null");
        }
        long handle = nativeCreate(
                config.getEndpoint(),
                config.getRegion(),
                config.getProjectId(),
                config.getTopicId(),
                config.getAccessKeyId(),
                config.getAccessKeySecret(),
                config.getSecurityToken(),
                config.getSource(),
                config.getHashKey(),
                config.getCompressType().ordinal(),
                config.getSendThreadCount(),
                config.isPersistent(),
                config.getPersistentFilePath(),
                config.isPersistentForceFlush(),
                config.getPersistentMaxFileCount(),
                config.getPersistentMaxFileSize(),
                config.getPersistentMaxLogCount(),
                config.getPacketLogBytes(),
                config.getPacketLogCount(),
                config.getPacketTimeoutMs(),
                config.getMaxBufferLimit(),
                config.getRetryCount(),
                config.getConnectTimeoutMs(),
                config.getRequestTimeoutMs(),
                config.isEnableTimeNs(),
                destroyWaitMs);
        if (handle == 0) {
            throw new IllegalStateException("native producer create failed");
        }
        return handle;
    }

    private static void requireNativeLibrary() {
        if (!NATIVE_LIBRARY_LOADED) {
            throw new IllegalStateException("tls_producer_jni is not loaded", NATIVE_LIBRARY_ERROR);
        }
    }

    private static native long nativeCreate(
            String endpoint,
            String region,
            String projectId,
            String topicId,
            String accessKeyId,
            String accessKeySecret,
            String securityToken,
            String source,
            String hashKey,
            int compressType,
            int sendThreadCount,
            boolean persistent,
            String persistentFilePath,
            boolean persistentForceFlush,
            int persistentMaxFileCount,
            int persistentMaxFileSize,
            int persistentMaxLogCount,
            int packetLogBytes,
            int packetLogCount,
            int packetTimeoutMs,
            int maxBufferLimit,
            int retryCount,
            int connectTimeoutMs,
            int requestTimeoutMs,
            boolean enableTimeNs,
            int destroyWaitMs);

    private static native int nativeUpdateEndpoint(long producerHandle, String endpoint, String region, String topicId);

    private static native int nativeResetSecurityToken(
            long producerHandle,
            String accessKeyId,
            String accessKeySecret,
            String securityToken);

    private static native void nativeDestroy(long producerHandle, int destroyWaitMs);
}
