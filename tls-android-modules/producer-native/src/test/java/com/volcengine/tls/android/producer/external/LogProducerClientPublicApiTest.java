package com.volcengine.tls.android.producer.external;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.tls.android.producer.internal.ConfigSnapshot;
import com.volcengine.tls.android.producer.internal.NativeProducerBridge;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertTrue;

public class LogProducerClientPublicApiTest {
    @Test
    public void awaitDestroy_isCallableFromOutsideProducerPackage() throws Exception {
        Constructor<LogProducerClient> constructor = LogProducerClient.class.getDeclaredConstructor(
                LogProducerConfig.class,
                LogProducerCallback.class,
                String.class,
                NativeProducerBridge.class);
        constructor.setAccessible(true);
        LogProducerClient client = constructor.newInstance(
                new LogProducerConfig()
                        .setEndpoint("https://tls-cn-beijing.volces.com")
                        .setRegion("cn-beijing")
                        .setTopicId("topic-id"),
                null,
                "external-test",
                new FakeBridge());

        client.addLog(new Log());
        client.destroyLogProducer();

        assertTrue(client.awaitDestroy(1000));
    }

    private static final class FakeBridge implements NativeProducerBridge {
        @Override
        public long create(ConfigSnapshot config, LogProducerCallback callback) {
            return 1;
        }

        @Override
        public void updateEndpoint(long producerHandle, String endpoint, String region, String topicId) {
        }

        @Override
        public void resetSecurityToken(long producerHandle, String accessKeyId, String accessKeySecret, String securityToken) {
        }

        @Override
        public void addLog(long producerHandle, Log log) {
        }

        @Override
        public void addLog(long producerHandle, Log log, int flush) {
        }

        @Override
        public void destroy(long producerHandle, int destroyWaitMs, int destroyFlusherWaitMs, int destroySenderWaitMs, boolean destroyWaitSplitEnabled) {
        }
    }
}
