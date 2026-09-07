package com.volcengine.tls.android.producer;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ProducerRealSendInstrumentedTest extends TestCase {

    public void testFlushOneRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes("android-real-flush-one", true);
    }

    public void testFlushZeroRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes("android-real-flush-zero", false);
    }

    public void testBufferedWalFlushOneRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes(
                "android-real-buffered-wal-flush-one",
                true,
                LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testBufferedWalFlushZeroRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes(
                "android-real-buffered-wal-flush-zero",
                false,
                LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncWalFlushOneRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes(
                "android-real-sync-wal-flush-one",
                true,
                LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    public void testSyncWalFlushZeroRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes(
                "android-real-sync-wal-flush-zero",
                false,
                LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    private void assertRealSendCompletes(String caseName, boolean immediateFlush) throws Exception {
        assertRealSendCompletes(caseName, immediateFlush, null);
    }

    private void assertRealSendCompletes(
            String caseName,
            boolean immediateFlush,
            LogProducerConfig.PersistentDurability durability) throws Exception {
        Properties props = loadConfig();
        CountDownLatch completion = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicInteger successCallbackCount = new AtomicInteger();
        AtomicReference<LogProducerResult> resultRef = new AtomicReference<>();
        File persistentDir = null;

        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(require(props, "endpoint"))
                .setRegion(require(props, "region"))
                .setAccessKeyId(require(props, "accessKeyId"))
                .setAccessKeySecret(require(props, "accessKeySecret"))
                .setSecurityToken(props.getProperty("securityToken", ""))
                .setTopicId(require(props, "topicId"))
                .setCompressType(parseCompressType(props.getProperty("compress", "lz4")))
                .setSendThreadCount(1)
                .setRetryMaxAttempts(1)
                .setCallbackFromSenderThread(true)
                .setPacketTimeoutMs(1000)
                .setConnectTimeoutMs(5000)
                .setRequestTimeoutMs(5000);
        if (durability != null) {
            Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            persistentDir = new File(
                    targetContext.getFilesDir(),
                    "producer-real-" + caseName + "-" + System.nanoTime());
            assertTrue("failed to create persistent test dir", persistentDir.mkdirs());
            config.setPersistent(true)
                    .setPersistentFilePath(persistentDir.getAbsolutePath())
                    .setPersistentMaxFileCount(4)
                    .setPersistentMaxFileSize(4096)
                    .setPersistentMaxLogCount(64)
                    .setPersistentDurability(durability);
        }

        LogProducerClient client = null;
        try {
            client = new LogProducerClient(config, result -> {
                callbackCount.incrementAndGet();
                if (result != null && result.isSuccess()) {
                    successCallbackCount.incrementAndGet();
                }
                resultRef.set(result);
                completion.countDown();
            });
            Log log = new Log()
                    .putContent("case", caseName)
                    .putContent("ts", String.valueOf(System.currentTimeMillis()))
                    .setLogTime(System.currentTimeMillis());
            if (immediateFlush) {
                client.addLog(log, 1);
            } else {
                client.addLog(log);
            }
            assertTrue("producer callback timed out", completion.await(20, TimeUnit.SECONDS));
            assertNotNull("producer callback result missing", resultRef.get());
            assertTrue("producer callback failed: " + resultRef.get().getFailureSummary(), resultRef.get().isSuccess());
            assertFalse("successful callback must not be retryable", resultRef.get().isRetryable());
            assertTrue("successful callback must expose its log id range", resultRef.get().hasLogIdRange());
        } finally {
            boolean destroyCompleted = true;
            if (client != null) {
                client.destroyLogProducer();
                destroyCompleted = client.awaitDestroy(5000);
                assertTrue("producer destroy timed out", destroyCompleted);
            }
            if (destroyCompleted && persistentDir != null) {
                deletePersistentTestDir(persistentDir);
            }
        }
        assertEquals("callback must be delivered exactly once", 1, callbackCount.get());
        assertEquals("successful callback must be delivered exactly once", 1, successCallbackCount.get());
    }

    private Properties loadConfig() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // Keep credentials in the instrumentation app's private files directory when provisioned.
        // The external-files fallback remains for existing scripts, but this test never logs values.
        File configFile = new File(context.getFilesDir(), "real_tls.properties");
        if (!configFile.isFile()) {
            File externalDir = context.getExternalFilesDir(null);
            assertNotNull("instrumentation external files dir missing", externalDir);
            configFile = new File(externalDir, "real_tls.properties");
        }
        assertTrue("missing real_tls.properties: " + configFile.getAbsolutePath(), configFile.isFile());

        Properties props = new Properties();
        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            props.load(inputStream);
        }
        return props;
    }

    private static void deletePersistentTestDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deletePersistentTestDir(file);
                } else {
                    assertTrue("failed to delete persistent test file", file.delete());
                }
            }
        }
        if (dir.exists()) {
            assertTrue("failed to delete persistent test dir", dir.delete());
        }
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        assertNotNull("missing property: " + key, value);
        assertTrue("empty property: " + key, !value.trim().isEmpty());
        return value;
    }

    private static LogProducerConfig.CompressType parseCompressType(String compress) {
        if ("none".equalsIgnoreCase(compress)) {
            return LogProducerConfig.CompressType.NONE;
        }
        if ("lz4".equalsIgnoreCase(compress)) {
            return LogProducerConfig.CompressType.LZ4;
        }
        throw new IllegalArgumentException("unsupported compress type: " + compress);
    }
}
