package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.NativeProducerBridge;
import com.volcengine.tls.android.producer.internal.ConfigSnapshot;
import com.volcengine.tls.android.producer.internal.JniNativeProducerBridge;
import com.volcengine.tls.android.producer.internal.ProcessUtil;

public final class LogProducerClient {
    private final Object lifecycleLock = new Object();
    private final ConfigSnapshot config;
    private final LogProducerCallback callback;
    private final NativeProducerBridge bridge;
    private volatile long producerHandle = 0;
    private volatile boolean destroyed;

    public LogProducerClient(LogProducerConfig config) {
        this(config, null);
    }

    public LogProducerClient(LogProducerConfig config, LogProducerCallback callback) {
        this(config, callback, ProcessUtil.getCurrentProcessName(), null);
    }

    LogProducerClient(LogProducerConfig config, LogProducerCallback callback, NativeProducerBridge bridge) {
        this(config, callback, ProcessUtil.getCurrentProcessName(), bridge);
    }

    LogProducerClient(LogProducerConfig config, LogProducerCallback callback, String processName, NativeProducerBridge bridge) {
        this.config = config == null ? null : new ConfigSnapshot(config, processName);
        this.callback = callback;
        this.bridge = bridge == null ? new JniNativeProducerBridge() : bridge;
    }

    static LogProducerClient forTest(LogProducerConfig config, NativeProducerBridge bridge) {
        return forTest(config, bridge, ProcessUtil.getCurrentProcessName());
    }

    static LogProducerClient forTest(LogProducerConfig config, NativeProducerBridge bridge, String processName) {
        return new LogProducerClient(config, null, processName, bridge);
    }

    public void addLog(Log log) {
        addLog(log, 0);
    }

    public void addLog(Log log, int flush) {
        synchronized (lifecycleLock) {
            long handle = ensureProducerLocked();
            if (bridge == null) {
                throw new UnsupportedOperationException("native bridge is not configured");
            }
            bridge.addLog(handle, log, flush);
        }
    }

    public void updateEndpoint(String endpoint, String region, String topicId) {
        synchronized (lifecycleLock) {
            long handle = ensureProducerLocked();
            if (bridge == null) {
                throw new UnsupportedOperationException("native bridge is not configured");
            }
            bridge.updateEndpoint(handle, endpoint, region, topicId);
        }
    }

    public void resetSecurityToken(String accessKeyId, String accessKeySecret, String securityToken) {
        synchronized (lifecycleLock) {
            long handle = ensureProducerLocked();
            if (bridge == null) {
                throw new UnsupportedOperationException("native bridge is not configured");
            }
            bridge.resetSecurityToken(handle, accessKeyId, accessKeySecret, securityToken);
        }
    }

    public void destroyLogProducer() {
        synchronized (lifecycleLock) {
            if (destroyed) {
                return;
            }
            destroyed = true;
            if (bridge == null) {
                return;
            }
            long handle = producerHandle;
            producerHandle = 0;
            bridge.destroyAsync(handle, config == null ? 0 : config.getDestroyWaitMs());
        }
    }

    private long ensureProducerLocked() {
        if (destroyed) {
            throw new IllegalStateException("producer destroyed");
        }
        if (producerHandle != 0) {
            return producerHandle;
        }
        if (bridge == null) {
            return 0;
        }
        if (bridge instanceof JniNativeProducerBridge) {
            producerHandle = ((JniNativeProducerBridge) bridge).create(config, callback);
            return producerHandle;
        }
        producerHandle = bridge.create(config == null ? null : config.toConfig(), callback);
        return producerHandle;
    }
}
