package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerConfig;

public interface NativeProducerBridge {
    long create(LogProducerConfig config, LogProducerCallback callback);

    void updateEndpoint(long producerHandle, String endpoint, String region, String topicId);

    void resetSecurityToken(long producerHandle, String accessKeyId, String accessKeySecret, String securityToken);

    void addLog(long producerHandle, Log log);

    void addLog(long producerHandle, Log log, int flush);

    void destroy(long producerHandle, int destroyWaitMs);

    void destroyAsync(long producerHandle, int destroyWaitMs);
}
