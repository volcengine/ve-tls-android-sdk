package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.NativeProducerBridge;

public final class LogProducerClient {
    private final LogProducerConfig config;
    private final LogProducerCallback callback;
    private final NativeProducerBridge bridge;
    private volatile long producerHandle = 0;
    private volatile boolean destroyed;

    public LogProducerClient(LogProducerConfig config) {
        this(config, null);
    }

    public LogProducerClient(LogProducerConfig config, LogProducerCallback callback) {
        this(config, callback, null);
    }

    LogProducerClient(LogProducerConfig config, LogProducerCallback callback, NativeProducerBridge bridge) {
        this.config = config;
        this.callback = callback;
        this.bridge = bridge;
    }

    static LogProducerClient forTest(LogProducerConfig config, NativeProducerBridge bridge) {
        return new LogProducerClient(config, null, bridge);
    }

    public void addLog(Log log) {
        addLog(log, 0);
    }

    public void addLog(Log log, int flush) {
        long handle = ensureProducer();
        if (bridge == null) {
            throw new UnsupportedOperationException("native bridge is not configured");
        }
        bridge.addLog(handle, log, flush);
    }

    public void updateEndpoint(String endpoint, String region, String topicId) {
        long handle = ensureProducer();
        if (bridge == null) {
            throw new UnsupportedOperationException("native bridge is not configured");
        }
        bridge.updateEndpoint(handle, endpoint, region, topicId);
    }

    public void resetSecurityToken(String accessKeyId, String accessKeySecret, String securityToken) {
        long handle = ensureProducer();
        if (bridge == null) {
            throw new UnsupportedOperationException("native bridge is not configured");
        }
        bridge.resetSecurityToken(handle, accessKeyId, accessKeySecret, securityToken);
    }

    public void destroyLogProducer() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        if (bridge == null) {
            return;
        }
        bridge.destroyAsync(producerHandle, config == null ? 0 : config.getDestroyWaitMs());
        producerHandle = 0;
    }

    private long ensureProducer() {
        if (destroyed) {
            throw new IllegalStateException("producer destroyed");
        }
        if (producerHandle != 0) {
            return producerHandle;
        }
        if (bridge == null) {
            return 0;
        }
        producerHandle = bridge.create(config, callback);
        return producerHandle;
    }
}
