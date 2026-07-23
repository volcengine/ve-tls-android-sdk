package com.volcengine.tls.android.producer.internal;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CallbackDispatcherTest {

    @Test
    public void dispatch_inlineWhenCallbackFromSenderThreadEnabled() {
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        Thread callerThread = Thread.currentThread();
        CallbackDispatcher dispatcher = new CallbackDispatcher(result -> {
            callbackCount.incrementAndGet();
            callbackThread.set(Thread.currentThread());
        }, true, null);

        dispatcher.dispatch(0, 200, "req", null, null, 0, 0, 1L, 1L);

        assertEquals(1, callbackCount.get());
        assertSame(callerThread, callbackThread.get());
    }

    @Test
    public void constructor_withoutMainThreadExecutorFailsFast() {
        assertThrows(IllegalStateException.class, () -> new CallbackDispatcher(
                result -> {
                },
                false,
                null));
    }

    @Test
    public void dispatch_rejectedExecutorDoesNotInvokeCallbackInline() {
        AtomicInteger callbackCount = new AtomicInteger();
        CallbackDispatcher dispatcher = new CallbackDispatcher(
                result -> callbackCount.incrementAndGet(),
                false,
                command -> {
                    throw new RejectedExecutionException("reject");
                });

        String stderr = captureStderr(() -> dispatcher.dispatch(0, 200, "req", null, null, 0, 0, 1L, 1L));

        assertEquals(0, callbackCount.get());
        assertTrue(stderr.contains("main-thread callback dispatch rejected"));
        assertTrue(stderr.contains("RejectedExecutionException"));
        assertEquals(1, dispatcher.getDispatchFailureCount());
        assertEquals("main-thread callback dispatch rejected", dispatcher.getLastDispatchFailureMessage());
    }

    private static String captureStderr(Runnable action) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(buffer, true);
        try {
            System.setErr(capture);
            action.run();
            capture.flush();
            return buffer.toString();
        } finally {
            System.setErr(original);
            capture.close();
        }
    }
}
