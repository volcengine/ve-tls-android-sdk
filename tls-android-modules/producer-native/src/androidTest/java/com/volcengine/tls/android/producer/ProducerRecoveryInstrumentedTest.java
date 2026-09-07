package com.volcengine.tls.android.producer;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Loopback transport with fake credentials; exercises Java -> JNI -> C -> HTTP. */
public final class ProducerRecoveryInstrumentedTest extends TestCase {
    public void testPersistentRetriesBeyondRequestBudget() throws Exception {
        try (TestServer server = new TestServer((attempt, headers) -> attempt < 3 ? 503 : 200)) {
            File dir = testDir();
            Completion completion = new Completion();
            LogProducerClient client = new LogProducerClient(config(server, dir), completion::accept);
            try {
                client.addLog(log(), 1);
                completion.assertSuccess();
                assertEquals(3, server.requests.get());
                assertFalse("retry must preserve the encoded log", server.bodyChanged.get());
            } finally {
                stop(client);
                deleteTestDir(dir);
            }
            server.assertHealthy();
            assertEquals("only the terminal success is reported", 1, completion.calls.get());
        }
    }

    public void testBufferedAuthRetainResumesAfterCredentialUpdate() throws Exception {
        assertAuthResume(LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncAuthRetainResumesAfterCredentialUpdate() throws Exception {
        assertAuthResume(LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    private void assertAuthResume(LogProducerConfig.PersistentDurability durability) throws Exception {
        try (TestServer server = new TestServer((attempt, headers) ->
                headers.contains("x-security-token: renewed-test-token") ? 200 : 403)) {
            File dir = testDir();
            Completion completion = new Completion();
            LogProducerClient client = new LogProducerClient(
                    config(server, dir).setPersistentDurability(durability), completion::accept);
            try {
                client.addLog(log(), 1);
                assertTrue("authentication request missing", server.firstFailure.await(10, TimeUnit.SECONDS));
                assertFalse("retained authentication failure is not terminal",
                        completion.done.await(300, TimeUnit.MILLISECONDS));
                client.resetSecurityToken("renewed-test-ak", "renewed-test-sk", "renewed-test-token");
                completion.assertSuccess();
                assertEquals("no retry until credentials change", 2, server.requests.get());
                assertFalse("credential update must preserve the batch", server.bodyChanged.get());
            } finally {
                stop(client);
                deleteTestDir(dir);
            }
            server.assertHealthy();
            assertEquals(1, completion.calls.get());
        }
    }

    public void testCloseDuringRetryRetainsWalForNextClient() throws Exception {
        AtomicBoolean available = new AtomicBoolean();
        try (TestServer server = new TestServer((attempt, headers) -> available.get() ? 200 : 503)) {
            File dir = testDir();
            Completion first = new Completion();
            LogProducerClient client = new LogProducerClient(config(server, dir), first::accept);
            try {
                client.addLog(log(), 1);
                assertTrue("must enter cross-cycle retry before close",
                        server.repeatedFailure.await(10, TimeUnit.SECONDS));
                stop(client);
                assertEquals("retry exhaustion is not terminal", 0, first.calls.get());
                available.set(true);
                Completion recovered = new Completion();
                LogProducerClient next = new LogProducerClient(config(server, dir), recovered::accept);
                try {
                    // Client creation is lazy. This opens/replays the existing WAL without adding a log.
                    next.updateEndpoint(server.endpoint(), "cn-beijing", "recovery-test");
                    recovered.assertSuccess();
                } finally {
                    stop(next);
                }
                assertEquals(1, recovered.calls.get());
            } finally {
                stop(client);
                deleteTestDir(dir);
            }
            server.assertHealthy();
        }
    }

    public void testMemoryBudgetExhaustionStillReportsFailure() throws Exception {
        try (TestServer server = new TestServer((attempt, headers) -> 503)) {
            Completion completion = new Completion();
            LogProducerClient client = new LogProducerClient(
                    config(server, null).setPersistent(false), completion::accept);
            try {
                client.addLog(log(), 1);
                assertTrue(completion.done.await(10, TimeUnit.SECONDS));
                assertFalse(completion.result.get().isSuccess());
                assertTrue(completion.result.get().isRetryable());
            } finally {
                stop(client);
            }
            server.assertHealthy();
            assertEquals(1, server.requests.get());
            assertEquals(1, completion.calls.get());
        }
    }

    public void testSocketTimeoutIsRetryableThroughJni() throws Exception {
        // Accept no requests: TCP connects, but the HTTP read must time out in Java.
        try (ServerSocket stalled = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            Completion completion = new Completion();
            LogProducerConfig config = new LogProducerConfig()
                    .setEndpoint("http://127.0.0.1:" + stalled.getLocalPort())
                    .setRegion("cn-beijing").setTopicId("recovery-test")
                    .setAccessKeyId("test-ak").setAccessKeySecret("test-sk")
                    .setCallbackFromSenderThread(true).setRetryMaxAttempts(1)
                    .setConnectTimeoutMs(1000).setRequestTimeoutMs(500)
                    .setDestroyFlusherWaitMs(1000).setDestroySenderWaitMs(1000);
            LogProducerClient client = new LogProducerClient(config, completion::accept);
            try {
                client.addLog(log(), 1);
                assertTrue(completion.done.await(10, TimeUnit.SECONDS));
                assertFalse(completion.result.get().isSuccess());
                assertEquals("JavaHttpBridgeError", completion.result.get().getErrorCode());
                assertTrue("cached JNI classifier must return true for transient IO",
                        completion.result.get().isRetryable());
            } finally {
                stop(client);
            }
            assertEquals(1, completion.calls.get());
        }
    }

    public void testMalformedUrlIsNonRetryableThroughJni() throws Exception {
        Completion completion = new Completion();
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint("http://[invalid")
                .setRegion("cn-beijing").setTopicId("recovery-test")
                .setAccessKeyId("test-ak").setAccessKeySecret("test-sk")
                .setCallbackFromSenderThread(true).setRetryMaxAttempts(3)
                .setDestroyFlusherWaitMs(1000).setDestroySenderWaitMs(1000);
        LogProducerClient client = new LogProducerClient(config, completion::accept);
        try {
            client.addLog(log(), 1);
            assertTrue(completion.done.await(10, TimeUnit.SECONDS));
            assertFalse(completion.result.get().isSuccess());
            assertEquals("JavaHttpBridgeError", completion.result.get().getErrorCode());
            assertFalse("JNI must honor terminal transport errors: "
                    + completion.result.get().getFailureSummary(), completion.result.get().isRetryable());
        } finally {
            stop(client);
        }
        assertEquals(1, completion.calls.get());
    }

    private static LogProducerConfig config(TestServer server, File dir) {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(server.endpoint()).setRegion("cn-beijing").setTopicId("recovery-test")
                .setAccessKeyId("test-ak").setAccessKeySecret("test-sk").setSecurityToken("old-test-token")
                .setCallbackFromSenderThread(true).setSendThreadCount(1)
                .setRetryMaxAttempts(1).setRetryTotalTimeoutMs(1000)
                .setRetryInitialIntervalMs(100).setRetryMaxIntervalMs(1000)
                .setPacketTimeoutMs(100).setConnectTimeoutMs(1000).setRequestTimeoutMs(1000)
                .setDestroyFlusherWaitMs(1000).setDestroySenderWaitMs(1000);
        if (dir != null) {
            config.setPersistent(true).setPersistentFilePath(dir.getAbsolutePath())
                    .setPersistentMaxFileCount(4).setPersistentMaxFileSize(65536)
                    .setPersistentMaxLogCount(64);
        }
        return config;
    }

    private static Log log() {
        return new Log().putContent("case", "core-032-recovery");
    }

    private static File testDir() {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "core-032-test-" + System.nanoTime());
    }

    private static void stop(LogProducerClient client) {
        client.destroyLogProducer();
        assertTrue("destroy must not wait for cross-cycle backoff", client.awaitDestroy(5000));
    }

    private static void deleteTestDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteTestDir(file);
                } else {
                    assertTrue(file.delete());
                }
            }
        }
        if (dir.exists()) {
            assertTrue(dir.delete());
        }
    }

    private static final class Completion {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<LogProducerResult> result = new AtomicReference<>();

        void accept(LogProducerResult value) {
            calls.incrementAndGet();
            result.set(value);
            done.countDown();
        }

        void assertSuccess() throws InterruptedException {
            assertTrue("terminal success timed out", done.await(15, TimeUnit.SECONDS));
            assertTrue(result.get().getFailureSummary(), result.get().isSuccess());
            assertTrue(result.get().hasLogIdRange());
        }
    }

    private interface Responder {
        int status(int attempt, String headers);
    }

    private static final class TestServer implements AutoCloseable {
        final AtomicInteger requests = new AtomicInteger();
        final CountDownLatch firstFailure = new CountDownLatch(1);
        final CountDownLatch repeatedFailure = new CountDownLatch(3);
        final AtomicBoolean bodyChanged = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final ServerSocket server;
        final Thread worker;
        volatile boolean closed;
        byte[] firstBody;

        TestServer(Responder responder) throws IOException {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                while (!closed) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(5000);
                        InputStream input = socket.getInputStream();
                        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                        int suffix = 0;
                        while (suffix != 0x0d0a0d0a) {
                            int value = input.read();
                            if (value < 0 || headerBytes.size() >= 32768) {
                                throw new IOException("invalid loopback request headers");
                            }
                            headerBytes.write(value);
                            suffix = (suffix << 8) | value;
                        }
                        String headers = new String(headerBytes.toByteArray(), StandardCharsets.US_ASCII)
                                .toLowerCase(Locale.ROOT);
                        int length = -1;
                        for (String line : headers.split("\r\n")) {
                            if (line.startsWith("content-length:")) {
                                length = Integer.parseInt(line.substring(15).trim());
                            }
                        }
                        if (length < 0 || length > 1024 * 1024) {
                            throw new IOException("invalid loopback request length");
                        }
                        byte[] body = new byte[length];
                        int offset = 0;
                        while (offset < length) {
                            int count = input.read(body, offset, length - offset);
                            if (count < 0) {
                                throw new IOException("truncated loopback body");
                            }
                            offset += count;
                        }
                        if (firstBody == null) {
                            firstBody = body;
                        } else if (!Arrays.equals(firstBody, body)) {
                            bodyChanged.set(true);
                        }
                        int status = responder.status(requests.incrementAndGet(), headers);
                        byte[] response = ("HTTP/1.1 " + status + " Test\r\n"
                                + "Content-Length: 0\r\nX-Tls-Requestid: loopback-test\r\n"
                                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                        socket.getOutputStream().write(response);
                        socket.getOutputStream().flush();
                        if (status >= 400) {
                            firstFailure.countDown();
                            repeatedFailure.countDown();
                        }
                    } catch (Throwable failure) {
                        if (!closed) {
                            error.compareAndSet(null, failure);
                        }
                        return;
                    }
                }
            }, "producer-loopback-test");
            worker.setDaemon(true);
            worker.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void assertHealthy() {
            assertNull("loopback server failed: " + error.get(), error.get());
        }

        @Override
        public void close() throws Exception {
            closed = true;
            server.close();
            worker.join(6000);
            assertFalse("loopback server did not stop", worker.isAlive());
        }
    }
}
