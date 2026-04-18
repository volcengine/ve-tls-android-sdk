package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.NativeProducerBridge;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void concurrentFirstAccess_createsProducerOnlyOnce() throws Exception {
        BlockingCreateBridge bridge = new BlockingCreateBridge();
        LogProducerClient client = LogProducerClient.forTest(new LogProducerConfig(), bridge, "demo");
        CountDownLatch start = new CountDownLatch(1);

        Thread first = new Thread(() -> awaitAndRun(start, () -> client.updateEndpoint("e1", "r1", "t1")));
        Thread second = new Thread(() -> awaitAndRun(start, () -> client.updateEndpoint("e2", "r2", "t2")));

        first.start();
        second.start();
        start.countDown();

        assertTrue(bridge.firstCreateEntered.await(1, TimeUnit.SECONDS));
        assertFalse("second thread should wait for the first create to finish",
                bridge.secondCreateEntered.await(200, TimeUnit.MILLISECONDS));

        bridge.releaseCreate.countDown();
        first.join(1000);
        second.join(1000);

        assertEquals(1, bridge.createCalls);
        assertEquals(2, bridge.updateEndpointCalls.get());
    }

    @Test
    public void destroyLogProducer_waitsForInflightBridgeCall() throws Exception {
        BlockingUpdateBridge bridge = new BlockingUpdateBridge();
        LogProducerClient client = LogProducerClient.forTest(
                new LogProducerConfig().setDestroyWaitMs(1),
                bridge,
                "demo");
        client.addLog(new Log());

        Thread updateThread = new Thread(() -> client.updateEndpoint("e1", "r1", "t1"));
        updateThread.start();

        assertTrue(bridge.updateEntered.await(1, TimeUnit.SECONDS));

        Thread destroyThread = new Thread(client::destroyLogProducer);
        destroyThread.start();

        assertFalse("destroy must not run while updateEndpoint is still in flight",
                bridge.destroyEntered.await(200, TimeUnit.MILLISECONDS));

        bridge.releaseUpdate.countDown();
        updateThread.join(1000);
        destroyThread.join(1000);

        assertEquals(1, bridge.destroyAsyncCalls);
        assertEquals(1, bridge.destroyWaitMs);
    }

    private static void awaitAndRun(CountDownLatch start, Runnable action) {
        try {
            assertTrue(start.await(1, TimeUnit.SECONDS));
            action.run();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static class FakeBridge implements NativeProducerBridge {
        protected int createCalls;
        private long lastCreateHandle;
        private String lastCreatePath;
        private int lastSendThreadCount;
        private LogProducerConfig createInputConfig;
        protected long destroyAsyncCalls;
        protected int destroyWaitMs;
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

    private static final class BlockingCreateBridge extends FakeBridge {
        private final CountDownLatch firstCreateEntered = new CountDownLatch(1);
        private final CountDownLatch secondCreateEntered = new CountDownLatch(1);
        private final CountDownLatch releaseCreate = new CountDownLatch(1);
        private final AtomicInteger updateEndpointCalls = new AtomicInteger();

        @Override
        public long create(LogProducerConfig config, LogProducerCallback callback) {
            long handle = super.create(config, callback);
            if (createCalls == 1) {
                firstCreateEntered.countDown();
            } else if (createCalls == 2) {
                secondCreateEntered.countDown();
            }
            try {
                assertTrue(releaseCreate.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            return handle;
        }

        @Override
        public void updateEndpoint(long producerHandle, String endpoint, String region, String topicId) {
            updateEndpointCalls.incrementAndGet();
            super.updateEndpoint(producerHandle, endpoint, region, topicId);
        }
    }

    private static final class BlockingUpdateBridge extends FakeBridge {
        private final CountDownLatch updateEntered = new CountDownLatch(1);
        private final CountDownLatch releaseUpdate = new CountDownLatch(1);
        private final CountDownLatch destroyEntered = new CountDownLatch(1);
        private final AtomicInteger updateEndpointCalls = new AtomicInteger();

        BlockingUpdateBridge() {
        }

        @Override
        public void updateEndpoint(long producerHandle, String endpoint, String region, String topicId) {
            updateEndpointCalls.incrementAndGet();
            updateEntered.countDown();
            try {
                assertTrue(releaseUpdate.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            super.updateEndpoint(producerHandle, endpoint, region, topicId);
        }

        @Override
        public void destroyAsync(long producerHandle, int destroyWaitMs) {
            destroyEntered.countDown();
            super.destroyAsync(producerHandle, destroyWaitMs);
        }
    }
}
