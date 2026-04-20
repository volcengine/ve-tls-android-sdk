package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerConfig;

public final class JniNativeProducerBridge implements NativeProducerBridge {
    private static final boolean NATIVE_LIBRARY_LOADED;
    private static final Throwable NATIVE_LIBRARY_ERROR;
    private volatile String defaultHashKey;

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
        int destroyWaitMs = config == null || config.isDestroyWaitSplitConfigured() ? 0 : config.getDestroyWaitMs();
        int destroyFlusherWaitMs = config == null ? 0 : config.getDestroyFlusherWaitMs();
        int destroySenderWaitMs = config == null ? 0 : config.getDestroySenderWaitMs();
        boolean destroyWaitSplitEnabled = config != null && config.isDestroyWaitSplitConfigured();
        return createInternal(nativeConfig, destroyWaitMs, destroyFlusherWaitMs, destroySenderWaitMs, destroyWaitSplitEnabled, callback);
    }

    @Override
    public long create(LogProducerConfig config, LogProducerCallback callback) {
        int destroyWaitMs = config == null || config.isDestroyWaitSplitConfigured() ? 0 : config.getDestroyWaitMs();
        int destroyFlusherWaitMs = config == null ? 0 : config.getDestroyFlusherWaitMs();
        int destroySenderWaitMs = config == null ? 0 : config.getDestroySenderWaitMs();
        boolean destroyWaitSplitEnabled = config != null && config.isDestroyWaitSplitConfigured();
        return createInternal(config, destroyWaitMs, destroyFlusherWaitMs, destroySenderWaitMs, destroyWaitSplitEnabled, callback);
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
    public void destroy(long producerHandle, int destroyWaitMs, int destroyFlusherWaitMs, int destroySenderWaitMs, boolean destroyWaitSplitEnabled) {
        if (producerHandle == 0) {
            return;
        }
        requireNativeLibrary();
        nativeDestroy(producerHandle, destroyWaitMs, destroyFlusherWaitMs, destroySenderWaitMs, destroyWaitSplitEnabled);
    }

    @Override
    public void destroyAsync(long producerHandle, int destroyWaitMs, int destroyFlusherWaitMs, int destroySenderWaitMs, boolean destroyWaitSplitEnabled) {
        if (producerHandle == 0) {
            return;
        }
        Thread destroyThread = new Thread(
                () -> destroy(producerHandle, destroyWaitMs, destroyFlusherWaitMs, destroySenderWaitMs, destroyWaitSplitEnabled),
                "tls-producer-destroy");
        destroyThread.setDaemon(true);
        destroyThread.start();
    }

    private long createInternal(LogProducerConfig config, int destroyWaitMs, int destroyFlusherWaitMs, int destroySenderWaitMs, boolean destroyWaitSplitEnabled, LogProducerCallback callback) {
        requireNativeLibrary();
        if (config == null) {
            throw new IllegalArgumentException("config == null");
        }
        CallbackDispatcher callbackDispatcher = callback == null
                ? null
                : new CallbackDispatcher(callback, config.isCallbackFromSenderThread());
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
                destroyWaitMs,
                destroyFlusherWaitMs,
                destroySenderWaitMs,
                destroyWaitSplitEnabled,
                callbackDispatcher);
        if (handle == 0) {
            throw new IllegalStateException("native producer create failed");
        }
        defaultHashKey = config.getHashKey();
        return handle;
    }

    @Override
    public void addLog(long producerHandle, Log log, int flush) {
        requireNativeLibrary();
        if (log == null) {
            throw new IllegalArgumentException("log == null");
        }
        String[] keys;
        String[] values;
        if (log.getContent().isEmpty()) {
            keys = new String[0];
            values = new String[0];
        } else {
            keys = new String[log.getContent().size()];
            values = new String[log.getContent().size()];
            int index = 0;
            for (java.util.Map.Entry<String, String> entry : log.getContent().entrySet()) {
                keys[index] = entry.getKey() == null ? "" : entry.getKey();
                values[index] = entry.getValue() == null ? "" : entry.getValue();
                index++;
            }
        }
        int result = nativeAddLog(producerHandle, log.getLogTime(), defaultHashKey, keys, values, flush);
        if (result != 0) {
            throw new IllegalStateException("native addLog failed: " + result);
        }
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
            int destroyWaitMs,
            int destroyFlusherWaitMs,
            int destroySenderWaitMs,
            boolean destroyWaitSplitEnabled,
            Object callbackDispatcher);

    private static native int nativeAddLog(
            long producerHandle,
            long logTimeMs,
            String hashKey,
            String[] keys,
            String[] values,
            int flush);

    private static native int nativeUpdateEndpoint(long producerHandle, String endpoint, String region, String topicId);

    private static native int nativeResetSecurityToken(
            long producerHandle,
            String accessKeyId,
            String accessKeySecret,
            String securityToken);

    private static native void nativeDestroy(
            long producerHandle,
            int destroyWaitMs,
            int destroyFlusherWaitMs,
            int destroySenderWaitMs,
            boolean destroyWaitSplitEnabled);
}
