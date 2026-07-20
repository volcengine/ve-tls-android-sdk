package com.volcengine.tls.android.producer;

public final class LogProducerClient {
    private final LogProducerCallback callback;
    private volatile boolean destroyed;

    public LogProducerClient(LogProducerConfig config) {
        this(config, null);
    }

    public LogProducerClient(LogProducerConfig config, LogProducerCallback callback) {
        this.callback = callback;
    }

    public void addLog(Log log) {
        addLog(log, 0);
    }

    public void addLog(Log log, int flush) {
        if (destroyed) {
            throw new IllegalStateException("producer destroyed");
        }
        if (callback != null) {
            callback.onCompletion(new LogProducerResult(LogProducerResult.Code.OK, "stub", null, null, 200, 0, 0, 0, 0));
        }
    }

    public void updateEndpoint(String endpoint, String region, String topicId) {}

    public void resetSecurityToken(String accessKeyId, String accessKeySecret, String securityToken) {}

    public void destroyLogProducer() {
        destroyed = true;
    }
}
