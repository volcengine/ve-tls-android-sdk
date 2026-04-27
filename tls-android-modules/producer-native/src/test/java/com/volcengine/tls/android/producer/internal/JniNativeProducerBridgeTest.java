package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.LogProducerCallback;
import com.volcengine.tls.android.producer.LogProducerConfig;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class JniNativeProducerBridgeTest {

    @Test
    public void create_passesLegacyDestroyAndOrderedTagsIntoNativeArgs() {
        AtomicReference<JniNativeProducerBridge.CreateArgs> capturedArgs = new AtomicReference<>();
        AtomicReference<Boolean> callbackFromSenderThread = new AtomicReference<>();
        Object dispatcher = new Object();
        LogProducerCallback callback = result -> {
        };
        JniNativeProducerBridge bridge = new JniNativeProducerBridge(
                () -> {
                },
                args -> {
                    capturedArgs.set(args);
                    return 42L;
                },
                (cb, senderThreadMode) -> {
                    callbackFromSenderThread.set(senderThreadMode);
                    assertSame(callback, cb);
                    return dispatcher;
                });
        ConfigSnapshot config = new ConfigSnapshot(
                new LogProducerConfig()
                        .setEndpoint("endpoint")
                        .setRegion("region")
                        .setProjectId("project")
                        .setTopicId("topic")
                        .setDestroyWaitMs(7)
                        .addTag("env", "prod")
                        .addTag("env", "canary"),
                "demo");

        long handle = bridge.create(config, callback);

        assertEquals(42L, handle);
        JniNativeProducerBridge.CreateArgs args = capturedArgs.get();
        assertNotNull(args);
        assertSame(config, args.getConfig());
        assertEquals(7, args.getDestroyWaitMs());
        assertEquals(0, args.getDestroyFlusherWaitMs());
        assertEquals(0, args.getDestroySenderWaitMs());
        assertEquals(0, args.getConfig().getRetryMaxAttempts());
        assertEquals(90_000, args.getConfig().getRetryTotalTimeoutMs());
        assertEquals(500, args.getConfig().getRetryInitialIntervalMs());
        assertEquals(10_000, args.getConfig().getRetryMaxIntervalMs());
        assertFalse(args.isDestroyWaitSplitEnabled());
        assertEquals(2, args.getLogTagCount());
        assertArrayEquals(new String[] {"env", "env"}, args.getLogTagKeys());
        assertArrayEquals(new String[] {"prod", "canary"}, args.getLogTagValues());
        assertSame(dispatcher, args.getCallbackDispatcher());
        assertFalse(callbackFromSenderThread.get());
    }

    @Test
    public void create_passesSplitDestroyAndSenderThreadCallbackModeIntoNativeArgs() {
        AtomicReference<JniNativeProducerBridge.CreateArgs> capturedArgs = new AtomicReference<>();
        AtomicReference<Boolean> callbackFromSenderThread = new AtomicReference<>();
        Object dispatcher = new Object();
        LogProducerCallback callback = result -> {
        };
        JniNativeProducerBridge bridge = new JniNativeProducerBridge(
                () -> {
                },
                args -> {
                    capturedArgs.set(args);
                    return 7L;
                },
                (cb, senderThreadMode) -> {
                    callbackFromSenderThread.set(senderThreadMode);
                    assertSame(callback, cb);
                    return dispatcher;
                });
        ConfigSnapshot config = new ConfigSnapshot(
                new LogProducerConfig()
                        .setEndpoint("endpoint")
                        .setRegion("region")
                        .setTopicId("topic")
                        .setRetryMaxAttempts(9)
                        .setRetryTotalTimeoutMs(12_000)
                        .setRetryInitialIntervalMs(600)
                        .setRetryMaxIntervalMs(6_000)
                        .setDestroyFlusherWaitMs(2)
                        .setDestroySenderWaitMs(3)
                        .setCallbackFromSenderThread(true),
                "demo");

        long handle = bridge.create(config, callback);

        assertEquals(7L, handle);
        JniNativeProducerBridge.CreateArgs args = capturedArgs.get();
        assertNotNull(args);
        assertEquals(0, args.getDestroyWaitMs());
        assertEquals(2, args.getDestroyFlusherWaitMs());
        assertEquals(3, args.getDestroySenderWaitMs());
        assertEquals(9, args.getConfig().getRetryMaxAttempts());
        assertEquals(12_000, args.getConfig().getRetryTotalTimeoutMs());
        assertEquals(600, args.getConfig().getRetryInitialIntervalMs());
        assertEquals(6_000, args.getConfig().getRetryMaxIntervalMs());
        assertTrue(args.isDestroyWaitSplitEnabled());
        assertEquals(0, args.getLogTagCount());
        assertArrayEquals(new String[0], args.getLogTagKeys());
        assertArrayEquals(new String[0], args.getLogTagValues());
        assertSame(dispatcher, args.getCallbackDispatcher());
        assertTrue(callbackFromSenderThread.get());
    }
}
