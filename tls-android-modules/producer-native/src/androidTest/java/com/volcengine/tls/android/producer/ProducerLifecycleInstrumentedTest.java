package com.volcengine.tls.android.producer;

import android.os.Looper;
import android.os.SystemClock;

import com.volcengine.tls.android.producer.internal.NativeProducerBridge;

import junit.framework.TestCase;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ProducerLifecycleInstrumentedTest extends TestCase {

    public void testDestroyWaitIsBoundedOnDevice() throws Exception {
        BlockingDestroyBridge bridge = new BlockingDestroyBridge();
        LogProducerClient client = new LogProducerClient(
                new LogProducerConfig()
                        .setEndpoint("https://tls-cn-beijing.volces.com")
                        .setRegion("cn-beijing")
                        .setTopicId("topic-id")
                        .setDestroyFlusherWaitMs(2)
                        .setDestroySenderWaitMs(3),
                null,
                "androidTest",
                bridge);
        client.addLog(new Log());

        client.destroyLogProducer();
        assertTrue(bridge.destroyEntered.await(1, TimeUnit.SECONDS));
        assertFalse(bridge.destroyThreadDaemon);

        long waitStartMs = SystemClock.elapsedRealtime();
        assertFalse(client.awaitDestroy(50));
        long waitedMs = SystemClock.elapsedRealtime() - waitStartMs;
        assertTrue("destroy wait should remain bounded", waitedMs < 500);

        bridge.releaseDestroy.countDown();

        assertTrue(client.awaitDestroy(1000));
        assertEquals(1L, bridge.destroyCalls);
        assertTrue(bridge.destroyWaitSplitEnabled);
        assertEquals(0, bridge.destroyWaitMs);
        assertEquals(2, bridge.destroyFlusherWaitMs);
        assertEquals(3, bridge.destroySenderWaitMs);
    }

    public void testCallbackDispatcherPostsToMainLooper() throws Exception {
        CountDownLatch callbackCompleted = new CountDownLatch(1);
        Thread[] callbackThread = new Thread[1];
        LogProducerCallback callback = result -> {
            callbackThread[0] = Thread.currentThread();
            callbackCompleted.countDown();
        };

        Object dispatcher = newCallbackDispatcher(callback, false);
        Thread senderThread = new Thread(() -> dispatchCompletion(dispatcher), "callback-dispatch-test");
        senderThread.start();
        senderThread.join(1000);

        assertTrue(callbackCompleted.await(5, TimeUnit.SECONDS));
        assertNotNull(callbackThread[0]);
        assertSame(Looper.getMainLooper().getThread(), callbackThread[0]);
    }

    private static Object newCallbackDispatcher(LogProducerCallback callback, boolean callbackFromSenderThread) throws Exception {
        Class<?> dispatcherClass = Class.forName("com.volcengine.tls.android.producer.internal.CallbackDispatcher");
        Constructor<?> constructor = dispatcherClass.getDeclaredConstructor(LogProducerCallback.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(callback, callbackFromSenderThread);
    }

    private static void dispatchCompletion(Object dispatcher) {
        try {
            Method dispatch = dispatcher.getClass().getDeclaredMethod(
                    "dispatch",
                    int.class,
                    int.class,
                    String.class,
                    String.class,
                    String.class,
                    int.class,
                    int.class,
                    long.class,
                    long.class);
            dispatch.setAccessible(true);
            dispatch.invoke(dispatcher, 0, 200, "request-id", null, null, 0, 0, 1L, 1L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class BlockingDestroyBridge implements NativeProducerBridge {
        private final CountDownLatch destroyEntered = new CountDownLatch(1);
        private final CountDownLatch releaseDestroy = new CountDownLatch(1);
        private volatile boolean destroyThreadDaemon = true;
        private volatile long destroyCalls;
        private volatile int destroyWaitMs;
        private volatile int destroyFlusherWaitMs;
        private volatile int destroySenderWaitMs;
        private volatile boolean destroyWaitSplitEnabled;

        @Override
        public long create(com.volcengine.tls.android.producer.internal.ConfigSnapshot config, LogProducerCallback callback) {
            return 1L;
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
            destroyThreadDaemon = Thread.currentThread().isDaemon();
            destroyEntered.countDown();
            try {
                assertTrue(releaseDestroy.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            destroyCalls++;
            this.destroyWaitMs = destroyWaitMs;
            this.destroyFlusherWaitMs = destroyFlusherWaitMs;
            this.destroySenderWaitMs = destroySenderWaitMs;
            this.destroyWaitSplitEnabled = destroyWaitSplitEnabled;
        }
    }
}
