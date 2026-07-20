package com.volcengine.tls.android.producer;

import android.content.Context;
import android.os.Bundle;

import java.io.File;
import java.util.Properties;

final class ProducerRealBenchmarkScenario {
    static final int DEFAULT_PACKET_LOG_BYTES = 1024 * 1024;
    static final int DEFAULT_PACKET_LOG_COUNT = 1024;
    static final int DEFAULT_PACKET_TIMEOUT_MS = 3000;
    static final int DEFAULT_MAX_BUFFER_LIMIT = 64 * 1024 * 1024;
    static final int DEFAULT_SEND_THREAD_COUNT = 1;
    static final int DEFAULT_RETRY_MAX_ATTEMPTS = 0;
    static final int DEFAULT_RETRY_TOTAL_TIMEOUT_MS = 90 * 1000;
    static final int DEFAULT_RETRY_INITIAL_INTERVAL_MS = 500;
    static final int DEFAULT_RETRY_MAX_INTERVAL_MS = 10 * 1000;
    static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    static final long DEFAULT_DESTROY_AWAIT_MS = 20000L;
    static final String DEFAULT_PERSISTENT_DIR_NAME = "benchmark-persistent";
    static final int DEFAULT_PERSISTENT_MAX_FILE_COUNT = 4;
    static final int DEFAULT_PERSISTENT_MAX_FILE_SIZE = 1024 * 1024;
    static final int DEFAULT_PERSISTENT_MAX_LOG_COUNT = 65536;

    private static final String ARG_RUN_ID = "benchmarkRunId";
    private static final String ARG_PROFILE = "benchmarkProfile";
    private static final String ARG_RATE_LPS = "benchmarkRateLps";
    private static final String ARG_DURATION_S = "benchmarkDurationS";
    private static final String ARG_SEND_THREAD_COUNT = "benchmarkSendThreadCount";
    private static final String ARG_RETRY_MAX_ATTEMPTS = "benchmarkRetryMaxAttempts";
    private static final String ARG_RETRY_TOTAL_TIMEOUT_MS = "benchmarkRetryTotalTimeoutMs";
    private static final String ARG_RETRY_INITIAL_INTERVAL_MS = "benchmarkRetryInitialIntervalMs";
    private static final String ARG_RETRY_MAX_INTERVAL_MS = "benchmarkRetryMaxIntervalMs";
    private static final String ARG_CONNECT_TIMEOUT_MS = "benchmarkConnectTimeoutMs";
    private static final String ARG_REQUEST_TIMEOUT_MS = "benchmarkRequestTimeoutMs";
    private static final String ARG_PACKET_TIMEOUT_MS = "benchmarkPacketTimeoutMs";
    private static final String ARG_PACKET_LOG_BYTES = "benchmarkPacketLogBytes";
    private static final String ARG_PACKET_LOG_COUNT = "benchmarkPacketLogCount";
    private static final String ARG_MAX_BUFFER_LIMIT = "benchmarkMaxBufferLimit";
    private static final String ARG_DESTROY_AWAIT_MS = "benchmarkDestroyAwaitMs";
    private static final String ARG_CALLBACK_FROM_SENDER_THREAD = "benchmarkCallbackFromSenderThread";
    private static final String ARG_COMPRESS = "benchmarkCompress";
    private static final String ARG_FAIL_ON_DEGRADED = "benchmarkFailOnDegraded";
    private static final String ARG_PERSISTENT = "benchmarkPersistent";
    private static final String ARG_PERSISTENT_DIR_NAME = "benchmarkPersistentDirName";
    private static final String ARG_PERSISTENT_MAX_FILE_COUNT = "benchmarkPersistentMaxFileCount";
    private static final String ARG_PERSISTENT_MAX_FILE_SIZE = "benchmarkPersistentMaxFileSize";
    private static final String ARG_PERSISTENT_MAX_LOG_COUNT = "benchmarkPersistentMaxLogCount";

    final String runId;
    final String profileName;
    final long rateLps;
    final int durationS;
    final int sendThreadCount;
    final int retryMaxAttempts;
    final int retryTotalTimeoutMs;
    final int retryInitialIntervalMs;
    final int retryMaxIntervalMs;
    final int connectTimeoutMs;
    final int requestTimeoutMs;
    final int packetTimeoutMs;
    final int packetLogBytes;
    final int packetLogCount;
    final int maxBufferLimit;
    final long destroyAwaitMs;
    final boolean callbackFromSenderThread;
    final LogProducerConfig.CompressType compressType;
    final boolean failOnDegraded;
    final boolean persistent;
    final String persistentDirName;
    final int persistentMaxFileCount;
    final int persistentMaxFileSize;
    final int persistentMaxLogCount;

    private ProducerRealBenchmarkScenario(
            String runId,
            String profileName,
            long rateLps,
            int durationS,
            int sendThreadCount,
            int retryMaxAttempts,
            int retryTotalTimeoutMs,
            int retryInitialIntervalMs,
            int retryMaxIntervalMs,
            int connectTimeoutMs,
            int requestTimeoutMs,
            int packetTimeoutMs,
            int packetLogBytes,
            int packetLogCount,
            int maxBufferLimit,
            long destroyAwaitMs,
            boolean callbackFromSenderThread,
            LogProducerConfig.CompressType compressType,
            boolean failOnDegraded,
            boolean persistent,
            String persistentDirName,
            int persistentMaxFileCount,
            int persistentMaxFileSize,
            int persistentMaxLogCount) {
        this.runId = runId;
        this.profileName = profileName;
        this.rateLps = rateLps;
        this.durationS = durationS;
        this.sendThreadCount = sendThreadCount;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryTotalTimeoutMs = retryTotalTimeoutMs;
        this.retryInitialIntervalMs = retryInitialIntervalMs;
        this.retryMaxIntervalMs = retryMaxIntervalMs;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.packetTimeoutMs = packetTimeoutMs;
        this.packetLogBytes = packetLogBytes;
        this.packetLogCount = packetLogCount;
        this.maxBufferLimit = maxBufferLimit;
        this.destroyAwaitMs = destroyAwaitMs;
        this.callbackFromSenderThread = callbackFromSenderThread;
        this.compressType = compressType;
        this.failOnDegraded = failOnDegraded;
        this.persistent = persistent;
        this.persistentDirName = persistentDirName;
        this.persistentMaxFileCount = persistentMaxFileCount;
        this.persistentMaxFileSize = persistentMaxFileSize;
        this.persistentMaxLogCount = persistentMaxLogCount;
    }

    static ProducerRealBenchmarkScenario from(Properties props, Bundle args) {
        String runId = firstNonEmpty(getArg(args, ARG_RUN_ID), props.getProperty("runId"), "benchmark-" + System.currentTimeMillis());
        String profile = firstNonEmpty(getArg(args, ARG_PROFILE), props.getProperty("benchmarkProfile"), "tls200");
        long rateLps = parseLong(firstNonEmpty(getArg(args, ARG_RATE_LPS), props.getProperty("benchmarkRateLps"), "100"));
        int durationS = (int) parseLong(firstNonEmpty(getArg(args, ARG_DURATION_S), props.getProperty("benchmarkDurationS"), "120"));
        int sendThreadCount = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_SEND_THREAD_COUNT),
                props.getProperty("sendThreadCount"),
                String.valueOf(DEFAULT_SEND_THREAD_COUNT)));
        int retryMaxAttempts = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_RETRY_MAX_ATTEMPTS),
                props.getProperty("retryMaxAttempts"),
                String.valueOf(DEFAULT_RETRY_MAX_ATTEMPTS)));
        int retryTotalTimeoutMs = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_RETRY_TOTAL_TIMEOUT_MS),
                props.getProperty("retryTotalTimeoutMs"),
                String.valueOf(DEFAULT_RETRY_TOTAL_TIMEOUT_MS)));
        int retryInitialIntervalMs = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_RETRY_INITIAL_INTERVAL_MS),
                props.getProperty("retryInitialIntervalMs"),
                String.valueOf(DEFAULT_RETRY_INITIAL_INTERVAL_MS)));
        int retryMaxIntervalMs = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_RETRY_MAX_INTERVAL_MS),
                props.getProperty("retryMaxIntervalMs"),
                String.valueOf(DEFAULT_RETRY_MAX_INTERVAL_MS)));
        int connectTimeoutMs = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_CONNECT_TIMEOUT_MS),
                props.getProperty("connectTimeoutMs"),
                String.valueOf(DEFAULT_CONNECT_TIMEOUT_MS)));
        int requestTimeoutMs = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_REQUEST_TIMEOUT_MS),
                props.getProperty("requestTimeoutMs"),
                String.valueOf(DEFAULT_REQUEST_TIMEOUT_MS)));
        int packetTimeoutMs = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_PACKET_TIMEOUT_MS),
                props.getProperty("packetTimeoutMs"),
                String.valueOf(DEFAULT_PACKET_TIMEOUT_MS)));
        int packetLogBytes = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_PACKET_LOG_BYTES),
                props.getProperty("packetLogBytes"),
                String.valueOf(DEFAULT_PACKET_LOG_BYTES)));
        int packetLogCount = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_PACKET_LOG_COUNT),
                props.getProperty("packetLogCount"),
                String.valueOf(DEFAULT_PACKET_LOG_COUNT)));
        int maxBufferLimit = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_MAX_BUFFER_LIMIT),
                props.getProperty("maxBufferLimit"),
                String.valueOf(DEFAULT_MAX_BUFFER_LIMIT)));
        long destroyAwaitMs = parseLong(firstNonEmpty(
                getArg(args, ARG_DESTROY_AWAIT_MS),
                props.getProperty("destroyAwaitMs"),
                String.valueOf(DEFAULT_DESTROY_AWAIT_MS)));
        boolean callbackFromSenderThread = parseBoolean(firstNonEmpty(
                getArg(args, ARG_CALLBACK_FROM_SENDER_THREAD),
                props.getProperty("callbackFromSenderThread"),
                "false"));
        LogProducerConfig.CompressType compressType = parseCompressType(firstNonEmpty(
                getArg(args, ARG_COMPRESS),
                props.getProperty("compress"),
                "lz4"));
        boolean failOnDegraded = parseBoolean(firstNonEmpty(
                getArg(args, ARG_FAIL_ON_DEGRADED),
                props.getProperty("failOnDegraded"),
                "true"));
        boolean persistent = parseBoolean(firstNonEmpty(
                getArg(args, ARG_PERSISTENT),
                props.getProperty("persistent"),
                "false"));
        String persistentDirName = firstNonEmpty(
                getArg(args, ARG_PERSISTENT_DIR_NAME),
                props.getProperty("persistentDirName"),
                DEFAULT_PERSISTENT_DIR_NAME);
        int persistentMaxFileCount = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_PERSISTENT_MAX_FILE_COUNT),
                props.getProperty("persistentMaxFileCount"),
                String.valueOf(DEFAULT_PERSISTENT_MAX_FILE_COUNT)));
        int persistentMaxFileSize = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_PERSISTENT_MAX_FILE_SIZE),
                props.getProperty("persistentMaxFileSize"),
                String.valueOf(DEFAULT_PERSISTENT_MAX_FILE_SIZE)));
        int persistentMaxLogCount = (int) parseLong(firstNonEmpty(
                getArg(args, ARG_PERSISTENT_MAX_LOG_COUNT),
                props.getProperty("persistentMaxLogCount"),
                String.valueOf(DEFAULT_PERSISTENT_MAX_LOG_COUNT)));
        return new ProducerRealBenchmarkScenario(
                runId,
                profile,
                rateLps,
                durationS,
                sendThreadCount,
                retryMaxAttempts,
                retryTotalTimeoutMs,
                retryInitialIntervalMs,
                retryMaxIntervalMs,
                connectTimeoutMs,
                requestTimeoutMs,
                packetTimeoutMs,
                packetLogBytes,
                packetLogCount,
                maxBufferLimit,
                destroyAwaitMs,
                callbackFromSenderThread,
                compressType,
                failOnDegraded,
                persistent,
                persistentDirName,
                persistentMaxFileCount,
                persistentMaxFileSize,
                persistentMaxLogCount);
    }

    LogProducerConfig buildConfig(Properties props, Context context) {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(require(props, "endpoint"))
                .setRegion(require(props, "region"))
                .setAccessKeyId(require(props, "accessKeyId"))
                .setAccessKeySecret(require(props, "accessKeySecret"))
                .setSecurityToken(props.getProperty("securityToken", ""))
                .setTopicId(require(props, "topicId"))
                .setCompressType(compressType)
                .setSendThreadCount(effectiveSendThreadCount())
                .setRetryMaxAttempts(retryMaxAttempts)
                .setRetryTotalTimeoutMs(retryTotalTimeoutMs)
                .setRetryInitialIntervalMs(retryInitialIntervalMs)
                .setRetryMaxIntervalMs(retryMaxIntervalMs)
                .setConnectTimeoutMs(connectTimeoutMs)
                .setRequestTimeoutMs(requestTimeoutMs)
                .setPacketTimeoutMs(packetTimeoutMs)
                .setPacketLogBytes(packetLogBytes)
                .setPacketLogCount(packetLogCount)
                .setMaxBufferLimit(maxBufferLimit)
                .setDestroyWaitMs((int) Math.min(Integer.MAX_VALUE, destroyAwaitMs))
                .setCallbackFromSenderThread(callbackFromSenderThread);
        if (persistent) {
            File path = persistentPath(context);
            if (!path.exists() && !path.mkdirs()) {
                throw new IllegalStateException("failed to create persistent dir: " + path.getAbsolutePath());
            }
            config.setPersistent(true)
                    .setPersistentFilePath(path.getAbsolutePath())
                    .setPersistentMaxFileCount(persistentMaxFileCount)
                    .setPersistentMaxFileSize(persistentMaxFileSize)
                    .setPersistentMaxLogCount(persistentMaxLogCount);
        }
        return config;
    }

    int effectiveSendThreadCount() {
        return persistent ? 1 : sendThreadCount;
    }

    File persistentPath(Context context) {
        File baseDir = new File(context.getFilesDir(), persistentDirName);
        return new File(baseDir, runId);
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing property: " + key);
        }
        return value.trim();
    }

    private static String getArg(Bundle args, String key) {
        return args == null ? null : args.getString(key);
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String firstNonEmpty(String primary, String secondary, String fallback) {
        String value = firstNonEmpty(primary, secondary);
        if (!value.isEmpty()) {
            return value;
        }
        return fallback;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid benchmark number: " + value, e);
        }
    }

    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
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
