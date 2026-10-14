package com.volcengine.tls.android.producer;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ProducerRealSustainedSendInstrumentedTest extends TestCase {
    private static final long NANOSECONDS_PER_SECOND = 1000000000L;
    private static final long NANOSECONDS_PER_MILLISECOND = 1000000L;
    private static final long DEFAULT_DURATION_S = 30L;
    private static final long DEFAULT_RATE_LPS = 1L;
    private static final long MAX_DURATION_S = 600L;
    private static final long MAX_RATE_LPS = 20L;
    private static final int PACKET_TIMEOUT_MS = 1000;
    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final int DESTROY_WAIT_MS = 10000;
    private static final long DESTROY_AWAIT_MS = 20000L;
    private static final int WAL_MAX_FILE_COUNT = 4;
    private static final int WAL_MAX_FILE_SIZE = 1024 * 1024;
    private static final int REQUEST_ID_SAMPLE_LIMIT = 3;

    public void testMemorySustainedRealSend() throws Exception {
        runSustained("memory", null);
    }

    public void testBufferedWalSustainedRealSend() throws Exception {
        runSustained("BUFFERED_WAL", LogProducerConfig.PersistentDurability.BUFFERED_WAL);
    }

    public void testSyncWalSustainedRealSend() throws Exception {
        runSustained("SYNC_WAL", LogProducerConfig.PersistentDurability.SYNC_WAL);
    }

    private void runSustained(
            final String mode,
            final LogProducerConfig.PersistentDurability durability) throws Exception {
        assertFalse("real sustained send requires a release BuildConfig", BuildConfig.DEBUG);

        Bundle arguments = InstrumentationRegistry.getArguments();
        long durationS = readBoundedPositiveArgument(
                arguments, "sustainedDurationS", DEFAULT_DURATION_S, MAX_DURATION_S);
        long rateLps = readBoundedPositiveArgument(
                arguments, "sustainedRateLps", DEFAULT_RATE_LPS, MAX_RATE_LPS);
        long expectedLogs = durationS * rateLps;
        final String runId = createRunId(mode);
        final Counters counters = new Counters();

        Context instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File walDir = null;
        LogProducerClient client = null;
        Throwable terminalFailure = null;
        long enqueueStartNs = 0L;
        long enqueueStartElapsedMs = 0L;
        long enqueueEndElapsedMs = 0L;
        long runEndElapsedMs = 0L;

        try {
            Properties props = loadConfig();
            LogProducerConfig config = new LogProducerConfig()
                    .setEndpoint(require(props, "endpoint"))
                    .setRegion(require(props, "region"))
                    .setAccessKeyId(require(props, "accessKeyId"))
                    .setAccessKeySecret(require(props, "accessKeySecret"))
                    .setSecurityToken(props.getProperty("securityToken", ""))
                    .setTopicId(require(props, "topicId"))
                    .setCompressType(LogProducerConfig.CompressType.LZ4)
                    .setSendThreadCount(1)
                    .setRetryMaxAttempts(RETRY_MAX_ATTEMPTS)
                    .setCallbackFromSenderThread(true)
                    .setPacketTimeoutMs(PACKET_TIMEOUT_MS)
                    .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                    .setRequestTimeoutMs(REQUEST_TIMEOUT_MS)
                    .setDestroyWaitMs(DESTROY_WAIT_MS);

            if (durability == null) {
                config.setPersistent(false);
            } else {
                walDir = createWalDirectory(targetContext, runId);
                config.setPersistent(true)
                        .setPersistentFilePath(walDir.getAbsolutePath())
                        .setPersistentMaxFileCount(WAL_MAX_FILE_COUNT)
                        .setPersistentMaxFileSize(WAL_MAX_FILE_SIZE)
                        .setPersistentMaxLogCount(persistentMaxLogCount(expectedLogs))
                        .setPersistentOverflowPolicy(
                                LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW)
                        .setPersistentDurability(durability);
            }

            client = new LogProducerClient(config, result -> recordCallback(counters, runId, mode, result));
            enqueueStartNs = System.nanoTime();
            enqueueStartElapsedMs = SystemClock.elapsedRealtime();
            try {
                for (long sequence = 1L; sequence <= expectedLogs; sequence++) {
                    long offsetNs = ((sequence - 1L) * NANOSECONDS_PER_SECOND) / rateLps;
                    paceUntil(enqueueStartNs + offsetNs);
                    counters.attempted.incrementAndGet();
                    Log log = new Log()
                            .putContent("validation_run", runId)
                            .putContent("validation_mode", mode)
                            .putContent("sequence", String.valueOf(sequence))
                            .setLogTime(System.currentTimeMillis());
                    try {
                        client.addLog(log);
                        counters.accepted.incrementAndGet();
                    } catch (Throwable failure) {
                        String failureClass = failure.getClass().getName();
                        counters.addFailureClass.compareAndSet("", failureClass);
                        System.out.println(
                                "SUSTAINED_ADD_FAILURE runID=" + runId
                                        + " mode=" + mode
                                        + " sequence=" + sequence
                                        + " exception=" + failureClass);
                        rethrow(failure);
                    }
                }
                paceUntil(enqueueStartNs + durationS * NANOSECONDS_PER_SECOND);
            } finally {
                enqueueEndElapsedMs = SystemClock.elapsedRealtime();
            }
        } catch (Throwable failure) {
            terminalFailure = failure;
        } finally {
            if (client != null) {
                try {
                    client.destroyLogProducer();
                    counters.destroyCompleted = client.awaitDestroy(DESTROY_AWAIT_MS);
                } catch (Throwable failure) {
                    if (terminalFailure == null) {
                        terminalFailure = failure;
                    }
                }
            }
            runEndElapsedMs = SystemClock.elapsedRealtime();
        }

        long enqueueWallMs = elapsedMs(enqueueStartElapsedMs, enqueueEndElapsedMs);
        long wallTotalMs = elapsedMs(enqueueStartElapsedMs, runEndElapsedMs);
        ProducerRealBenchmarkDelivery.Summary delivery = counters.delivery.snapshot(
                counters.accepted.get());
        boolean coverageComplete = delivery.isComplete()
                && delivery.successfulLogs == expectedLogs
                && delivery.remainingLogs == 0L;
        boolean successful = terminalFailure == null
                && counters.destroyCompleted
                && counters.attempted.get() == expectedLogs
                && counters.accepted.get() == expectedLogs
                && counters.callbackFailure.get() == 0L
                && counters.callbackSuccess.get() > 0L
                && coverageComplete;
        String status = successful ? "OK" : "FAIL";

        File reportFile = null;
        Throwable reportFailure = null;
        try {
            reportFile = writeReport(
                    instrumentationContext,
                    runId,
                    mode,
                    durability,
                    durationS,
                    rateLps,
                    expectedLogs,
                    counters,
                    delivery,
                    enqueueWallMs,
                    wallTotalMs,
                    status,
                    walDir,
                    terminalFailure);
        } catch (Throwable failure) {
            reportFailure = failure;
            if (terminalFailure == null) {
                terminalFailure = failure;
            }
            successful = false;
            status = "FAIL";
        }

        System.out.println(
                "SUSTAINED_REPORT path=" + (reportFile == null ? "<unwritten>" : reportFile.getAbsolutePath())
                        + " status=" + status
                        + " runID=" + runId
                        + " mode=" + mode
                        + " attempted=" + counters.attempted.get()
                        + " accepted=" + counters.accepted.get()
                        + " successful=" + delivery.successfulLogs
                        + " remaining=" + delivery.remainingLogs
                        + " callbackSuccess=" + counters.callbackSuccess.get()
                        + " callbackFailure=" + counters.callbackFailure.get()
                        + " firstFailure=" + counters.firstFailure.get()
                        + " enqueueWallMs=" + enqueueWallMs
                        + " wallTotalMs=" + wallTotalMs
                        + " sdkVersion=" + BuildConfig.SDK_VERSION
                        + " apiLevel=" + Build.VERSION.SDK_INT
                        + " requestIds=" + joinRequestIds(counters.requestIds));

        if (reportFailure != null) {
            rethrow(reportFailure);
        }
        if (terminalFailure != null) {
            rethrow(terminalFailure);
        }

        assertEquals("attempted logs must equal duration*rate", expectedLogs, counters.attempted.get());
        assertEquals("all expected logs must be accepted", expectedLogs, counters.accepted.get());
        assertTrue("destroy did not complete", counters.destroyCompleted);
        assertEquals("callback failures must be zero", 0L, counters.callbackFailure.get());
        assertTrue("successful callback coverage is incomplete", coverageComplete);
        assertEquals("successful logs must equal duration*rate", expectedLogs, delivery.successfulLogs);
        assertEquals("remaining logs must be zero", 0L, delivery.remainingLogs);
    }

    private static File createWalDirectory(Context context, String runId) {
        File baseDir = new File(context.getFilesDir(), "producer-real-sustained");
        File walDir = new File(baseDir, runId);
        if (walDir.exists()) {
            throw new IllegalStateException("WAL directory already exists");
        }
        if (!walDir.mkdirs()) {
            throw new IllegalStateException("failed to create private WAL directory");
        }
        // Keep this run's private directory as evidence; never delete a shared directory.
        return walDir;
    }

    private static void recordCallback(
            Counters counters,
            String runId,
            String mode,
            LogProducerResult result) {
        if (result != null && result.isSuccess()) {
            counters.callbackSuccess.incrementAndGet();
            if (result.hasLogIdRange()) {
                counters.delivery.recordSuccessRange(result.getStartId(), result.getEndId());
            } else {
                counters.delivery.recordSuccessRange(0L, 0L);
            }
            String requestId = safeRequestId(result.getRequestId());
            if (!requestId.isEmpty()) {
                synchronized (counters.requestIds) {
                    if (counters.requestIds.size() < REQUEST_ID_SAMPLE_LIMIT
                            && !counters.requestIds.contains(requestId)) {
                        counters.requestIds.add(requestId);
                    }
                }
            }
            return;
        }

        counters.callbackFailure.incrementAndGet();
        String facts = failureFacts(result);
        if (counters.firstFailure.compareAndSet("", facts)) {
            System.out.println(
                    "SUSTAINED_CALLBACK_FAILURE runID=" + runId
                            + " mode=" + mode
                            + " " + facts);
        }
    }

    private static String failureFacts(LogProducerResult result) {
        String code = "NULL";
        String kind = "UNKNOWN";
        int httpCode = 0;
        if (result != null) {
            if (result.getCode() != null) {
                code = result.getCode().name();
                if (result.getFailureKind() != null) {
                    kind = result.getFailureKind().name();
                }
            }
            httpCode = result.getHttpCode();
        }
        return "code=" + code + " kind=" + kind + " httpcode=" + httpCode;
    }

    private static String safeRequestId(String value) {
        if (value == null || value.length() == 0 || value.length() > 128) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isRequestIdChar(c)) {
                return "";
            }
        }
        return value;
    }

    private static boolean isRequestIdChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == ':';
    }

    private static long readBoundedPositiveArgument(
            Bundle arguments,
            String key,
            long defaultValue,
            long maximum) {
        String value = arguments == null ? null : arguments.getString(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        final long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + key, e);
        }
        if (parsed <= 0L || parsed > maximum) {
            throw new IllegalArgumentException(key + " must be in (0, " + maximum + "]");
        }
        return parsed;
    }

    private static String createRunId(String mode) {
        return "sustained-"
                + safePathComponent(BuildConfig.SDK_VERSION)
                + "-"
                + safePathComponent(mode)
                + "-"
                + System.currentTimeMillis()
                + "-"
                + System.nanoTime();
    }

    private static String safePathComponent(String value) {
        if (value == null || value.length() == 0) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            } else {
                builder.append('-');
            }
        }
        return builder.toString();
    }

    private static void paceUntil(long targetNs) throws InterruptedException {
        while (true) {
            long remainingNs = targetNs - System.nanoTime();
            if (remainingNs <= 0L) {
                return;
            }
            if (remainingNs >= NANOSECONDS_PER_MILLISECOND) {
                long sleepMs = remainingNs / NANOSECONDS_PER_MILLISECOND;
                int sleepNanos = (int) (remainingNs % NANOSECONDS_PER_MILLISECOND);
                Thread.sleep(sleepMs, sleepNanos);
            } else {
                Thread.yield();
            }
        }
    }

    private static long elapsedMs(long startMs, long endMs) {
        if (startMs <= 0L || endMs < startMs) {
            return 0L;
        }
        return endMs - startMs;
    }

    private File writeReport(
            Context context,
            String runId,
            String mode,
            LogProducerConfig.PersistentDurability durability,
            long durationS,
            long rateLps,
            long expectedLogs,
            Counters counters,
            ProducerRealBenchmarkDelivery.Summary delivery,
            long enqueueWallMs,
            long wallTotalMs,
            String status,
            File walDir,
            Throwable terminalFailure) throws IOException {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            externalDir = context.getFilesDir();
        }
        File reportDir = new File(externalDir, "sustained");
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            throw new IOException("failed to create sustained report directory");
        }
        File reportFile = new File(reportDir, runId + ".properties");
        Properties report = new Properties();
        report.setProperty("status", status);
        report.setProperty("runId", runId);
        report.setProperty("mode", mode);
        report.setProperty("durationS", String.valueOf(durationS));
        report.setProperty("rateLps", String.valueOf(rateLps));
        report.setProperty("expectedLogs", String.valueOf(expectedLogs));
        report.setProperty("attempted", String.valueOf(counters.attempted.get()));
        report.setProperty("accepted", String.valueOf(counters.accepted.get()));
        report.setProperty("successful", String.valueOf(delivery.successfulLogs));
        report.setProperty("remaining", String.valueOf(delivery.remainingLogs));
        report.setProperty("callbackSuccess", String.valueOf(counters.callbackSuccess.get()));
        report.setProperty("callbackFailure", String.valueOf(counters.callbackFailure.get()));
        report.setProperty("invalidSuccessRanges", String.valueOf(delivery.invalidSuccessRanges));
        report.setProperty("destroyCompleted", String.valueOf(counters.destroyCompleted));
        report.setProperty("enqueueWallMs", String.valueOf(enqueueWallMs));
        report.setProperty("wallTotalMs", String.valueOf(wallTotalMs));
        report.setProperty("sdkVersion", BuildConfig.SDK_VERSION);
        report.setProperty("apiLevel", String.valueOf(Build.VERSION.SDK_INT));
        report.setProperty("buildConfigDebug", String.valueOf(BuildConfig.DEBUG));
        report.setProperty("requestIds", joinRequestIds(counters.requestIds));
        report.setProperty("firstFailure", counters.firstFailure.get());
        report.setProperty("addFailureClass", counters.addFailureClass.get());
        report.setProperty(
                "terminalFailureClass",
                terminalFailure == null ? "" : terminalFailure.getClass().getName());
        report.setProperty("walPath", walDir == null ? "" : walDir.getAbsolutePath());
        report.setProperty("durability", durability == null ? "memory" : durability.name());
        report.setProperty("compress", "lz4");
        report.setProperty("sendThreadCount", "1");
        report.setProperty("packetTimeoutMs", String.valueOf(PACKET_TIMEOUT_MS));
        report.setProperty("retryMaxAttempts", String.valueOf(RETRY_MAX_ATTEMPTS));
        report.setProperty("connectTimeoutMs", String.valueOf(CONNECT_TIMEOUT_MS));
        report.setProperty("requestTimeoutMs", String.valueOf(REQUEST_TIMEOUT_MS));
        report.setProperty("destroyWaitMs", String.valueOf(DESTROY_WAIT_MS));
        report.setProperty("destroyAwaitMs", String.valueOf(DESTROY_AWAIT_MS));
        try (FileOutputStream output = new FileOutputStream(reportFile)) {
            report.store(output, "producer-native sustained real send");
        }
        return reportFile;
    }

    private Properties loadConfig() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // Match ProducerRealSendInstrumentedTest: private files first, external files as fallback.
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

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        assertNotNull("missing property: " + key, value);
        assertTrue("empty property: " + key, !value.trim().isEmpty());
        return value;
    }

    private static int persistentMaxLogCount(long expectedLogs) {
        long withHeadroom = expectedLogs + 1024L;
        return (int) Math.min(Integer.MAX_VALUE, withHeadroom);
    }

    private static String joinRequestIds(List<String> requestIds) {
        StringBuilder result = new StringBuilder();
        synchronized (requestIds) {
            for (String requestId : requestIds) {
                if (result.length() > 0) {
                    result.append(',');
                }
                result.append(requestId);
            }
        }
        return result.toString();
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        throw new RuntimeException(failure);
    }

    private static final class Counters {
        final AtomicLong attempted = new AtomicLong();
        final AtomicLong accepted = new AtomicLong();
        final AtomicLong callbackSuccess = new AtomicLong();
        final AtomicLong callbackFailure = new AtomicLong();
        final AtomicReference<String> firstFailure = new AtomicReference<>("");
        final AtomicReference<String> addFailureClass = new AtomicReference<>("");
        final ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        final List<String> requestIds = new ArrayList<>();
        volatile boolean destroyCompleted;
    }
}
