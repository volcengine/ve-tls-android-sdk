package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.NativeProducerBridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LogProducerClientBridgeTest {

    @Test
    public void create_clonesConfigAndRewritesPersistentPathForSecondaryProcess() {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint("https://tls-cn-beijing.volces.com")
                .setRegion("cn-beijing")
                .setProjectId("project-id")
                .setTopicId("topic-id")
                .setPersistent(true)
                .setPersistentFilePath("/data/user/0/demo/files/tls/producer")
                .setPersistentMaxFileCount(4)
                .setPersistentMaxFileSize(1024)
                .setPersistentMaxLogCount(1024)
                .setSendThreadCount(4);

        FakeBridge bridge = new FakeBridge();

        LogProducerClient client = LogProducerClient.forTest(config, bridge, "demo:push");
        client.addLog(new Log());
        assertEquals(1, bridge.createCalls);
        assertNotSame(config, bridge.createInputConfig);
        assertTrue(bridge.lastCreatePath.contains("demo_push"));
        assertEquals(1, bridge.lastSendThreadCount);
        assertEquals("/data/user/0/demo/files/tls/producer", config.getPersistentFilePath());
        assertEquals(4, config.getSendThreadCount());
    }

    @Test
    public void create_doesNotRewritePersistentPathForMainProcess() {
        LogProducerConfig config = new LogProducerConfig()
                .setPersistent(true)
                .setPersistentFilePath("/data/user/0/demo/files/tls/producer")
                .setSendThreadCount(8);

        FakeBridge bridge = new FakeBridge();

        LogProducerClient client = LogProducerClient.forTest(config, bridge, "demo");
        client.addLog(new Log());
        assertEquals("/data/user/0/demo/files/tls/producer", bridge.lastCreatePath);
        assertEquals(1, bridge.lastSendThreadCount);
    }

    @Test
    public void updateEndpointAndResetSecurityTokenAreForwardedToBridge() {
        FakeBridge bridge = new FakeBridge();
        LogProducerClient client = LogProducerClient.forTest(
                new LogProducerConfig()
                        .setEndpoint("old-endpoint")
                        .setRegion("old-region")
                        .setTopicId("old-topic")
                        .setProjectId("project-id"),
                bridge,
                "demo");

        client.updateEndpoint("new-endpoint", "new-region", "new-topic");
        assertEquals("new-endpoint", bridge.lastEndpoint);
        assertEquals("new-region", bridge.lastRegion);
        assertEquals("new-topic", bridge.lastTopicId);

        client.resetSecurityToken("new-ak", "new-secret", "new-token");
        assertEquals("new-ak", bridge.lastAccessKeyId);
        assertEquals("new-secret", bridge.lastAccessKeySecret);
        assertEquals("new-token", bridge.lastSecurityToken);
    }

    @Test
    public void destroyLogProducer_rejectsAddLog() {
        FakeBridge bridge = new FakeBridge();
        LogProducerClient client = LogProducerClient.forTest(new LogProducerConfig(), bridge, "demo");

        client.destroyLogProducer();
        assertEquals(1, bridge.destroyAsyncCalls);

        assertThrows(IllegalStateException.class, () -> client.addLog(new Log()));
    }

    private static final class FakeBridge implements NativeProducerBridge {
        private int createCalls;
        private long lastCreateHandle;
        private String lastCreatePath;
        private int lastSendThreadCount;
        private LogProducerConfig createInputConfig;
        private long destroyAsyncCalls;
        private int destroyWaitMs;
        private String lastEndpoint;
        private String lastRegion;
        private String lastTopicId;
        private String lastAccessKeyId;
        private String lastAccessKeySecret;
        private String lastSecurityToken;

        @Override
        public long create(LogProducerConfig config, LogProducerCallback callback) {
            createCalls++;
            createInputConfig = config;
            lastCreatePath = config.getPersistentFilePath();
            lastSendThreadCount = config.getSendThreadCount();
            lastCreateHandle = 1000L + createCalls;
            return lastCreateHandle;
        }

        @Override
        public void updateEndpoint(long producerHandle, String endpoint, String region, String topicId) {
            lastEndpoint = endpoint;
            lastRegion = region;
            lastTopicId = topicId;
        }

        @Override
        public void resetSecurityToken(long producerHandle, String accessKeyId, String accessKeySecret, String securityToken) {
            lastAccessKeyId = accessKeyId;
            lastAccessKeySecret = accessKeySecret;
            lastSecurityToken = securityToken;
        }

        @Override
        public void addLog(long producerHandle, Log log) {
            // no-op
        }

        @Override
        public void addLog(long producerHandle, Log log, int flush) {
            // no-op
        }

        @Override
        public void destroy(long producerHandle, int destroyWaitMs) {
            // no-op
        }

        @Override
        public void destroyAsync(long producerHandle, int destroyWaitMs) {
            destroyAsyncCalls++;
            this.destroyWaitMs = destroyWaitMs;
        }
    }
}
