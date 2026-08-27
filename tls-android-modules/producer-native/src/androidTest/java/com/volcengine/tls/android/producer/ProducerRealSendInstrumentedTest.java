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
import java.util.concurrent.atomic.AtomicReference;

public final class ProducerRealSendInstrumentedTest extends TestCase {

    public void testFlushOneRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes("android-real-flush-one", true);
    }

    public void testFlushZeroRealSendCompletesWithoutCrashing() throws Exception {
        assertRealSendCompletes("android-real-flush-zero", false);
    }

    private void assertRealSendCompletes(String caseName, boolean immediateFlush) throws Exception {
        Properties props = loadConfig();
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<LogProducerResult> resultRef = new AtomicReference<>();

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

        LogProducerClient client = new LogProducerClient(config, result -> {
            resultRef.set(result);
            completion.countDown();
        });
        try {
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
            client.destroyLogProducer();
            client.awaitDestroy(5000);
        }
    }

    private Properties loadConfig() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        File externalDir = context.getExternalFilesDir(null);
        assertNotNull("instrumentation external files dir missing", externalDir);
        File configFile = new File(externalDir, "real_tls.properties");
        assertTrue("missing real_tls.properties: " + configFile.getAbsolutePath(), configFile.isFile());

        Properties props = new Properties();
        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            props.load(inputStream);
        }
        return props;
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
