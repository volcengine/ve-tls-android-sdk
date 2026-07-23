package com.volcengine.tls.android.demo;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;

import com.volcengine.tls.android.producer.Log;
import com.volcengine.tls.android.producer.LogProducerClient;
import com.volcengine.tls.android.producer.LogProducerConfig;
import com.volcengine.tls.android.producer.LogProducerResult;
import com.volcengine.tls.android.producer.internal.JniNativeProducerBridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class FormalBenchmarkRunner {
    private static final long NS_PER_SECOND = 1_000_000_000L;
    private static final long SAMPLE_INTERVAL_MS = 200L;
    private static final int DEFAULT_DURATION_S = 120;
    private static final int DEFAULT_STEADY_STATE_DURATION_S = 60;
    private static final int DEFAULT_PACKET_LOG_BYTES = 1024 * 1024;
    private static final int DEFAULT_PACKET_LOG_COUNT = 1024;
    private static final int DEFAULT_PACKET_TIMEOUT_MS = 3000;
    private static final int DEFAULT_MAX_BUFFER_LIMIT = 64 * 1024 * 1024;
    private static final int DEFAULT_SEND_THREAD_COUNT = 1;
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 0;
    private static final int DEFAULT_RETRY_TOTAL_TIMEOUT_MS = 90 * 1000;
    private static final int DEFAULT_RETRY_INITIAL_INTERVAL_MS = 500;
    private static final int DEFAULT_RETRY_MAX_INTERVAL_MS = 10 * 1000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    private static final long DEFAULT_DESTROY_AWAIT_MS = 20_000L;
    private static final int DEFAULT_PERSISTENT_MAX_FILE_COUNT = 32;
    private static final int DEFAULT_PERSISTENT_MAX_FILE_SIZE = 8 * 1024 * 1024;
    private static final int DEFAULT_PERSISTENT_MAX_LOG_COUNT = 65_536;

    private static final String[][] FIXED_TAGS = new String[][] {
            {"tag_1", "val_1"},
            {"tag_2", "val_2"},
            {"tag_3", "val_3"},
            {"tag_4", "val_4"},
            {"tag_5", "val_5"}
    };

    interface Listener {
        void onEvent(String line);
    }

    static final class RunArtifacts {
        final String runId;
        final File resultDir;
        final File summaryFile;
        final List<FormalBenchmarkReport> reports;

        RunArtifacts(String runId, File resultDir, File summaryFile, List<FormalBenchmarkReport> reports) {
            this.runId = runId;
            this.resultDir = resultDir;
            this.summaryFile = summaryFile;
            this.reports = reports;
        }
    }

    private FormalBenchmarkRunner() {
    }

    static RunArtifacts runAll(Context context, Properties props, AtomicBoolean cancelled, Listener listener) throws Exception {
        return runAll(context, props, cancelled, listener, FormalBenchmarkPlan.defaultScenarios());
    }

    static RunArtifacts runAll(Context context, Properties props, AtomicBoolean cancelled, Listener listener, List<FormalBenchmarkPlan.Scenario> scenarios) throws Exception {
        String runId = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File root = context.getExternalFilesDir("formal-benchmark");
        if (root == null) {
            root = new File(context.getFilesDir(), "formal-benchmark");
        }
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("failed to create benchmark root: " + root.getAbsolutePath());
        }
        File resultDir = new File(root, runId);
        if (!resultDir.exists() && !resultDir.mkdirs()) {
            throw new IllegalStateException("failed to create benchmark dir: " + resultDir.getAbsolutePath());
        }
        listener.onEvent("Formal benchmark runId=" + runId);
        listener.onEvent("Result dir=" + resultDir.getAbsolutePath());

        List<FormalBenchmarkReport> reports = new ArrayList<>();
        for (FormalBenchmarkPlan.Scenario scenario : scenarios) {
            if (cancelled.get()) {
                listener.onEvent("Benchmark cancelled before " + scenario.scenarioId());
                break;
            }
            listener.onEvent("Running " + scenario.scenarioId());
            FormalBenchmarkReport report = runScenario(context, props, runId, scenario, cancelled);
            reports.add(report);
            writeProperties(new File(resultDir, scenario.scenarioId() + ".properties"), report.toProperties());
            listener.onEvent(report.summaryLine());
        }

        File summaryFile = new File(resultDir, "summary.md");
        writeString(summaryFile, FormalBenchmarkSummary.renderMarkdown(runId, reports));
        return new RunArtifacts(runId, resultDir, summaryFile, Collections.unmodifiableList(reports));
    }

    private static FormalBenchmarkReport runScenario(Context context, Properties props, String runId, FormalBenchmarkPlan.Scenario scenario, AtomicBoolean cancelled) throws Exception {
        BenchmarkCounters counters = new BenchmarkCounters();
        JniNativeProducerBridge.resetAddLogPerfStats();
        JniNativeProducerBridge.resetSenderPerfStats();
        LogProducerConfig config = buildConfig(context, props, runId, scenario);
        for (String[] tag : FIXED_TAGS) {
            config.addTag(tag[0], tag[1]);
        }
        BenchmarkPayload.warmUp();

        LogProducerClient client = new LogProducerClient(config, result -> handleCompletion(result, counters));

        AtomicBoolean sampling = new AtomicBoolean(true);
        AtomicBoolean steadySampling = new AtomicBoolean(true);
        AtomicLong steadyStartElapsedMs = new AtomicLong(Long.MAX_VALUE);
        Thread sampler = new Thread(() -> {
            long samplerCpuStartNs = Debug.threadCpuTimeNanos();
            long steadySamplerCpuStartNs = -1L;
            while (sampling.get()) {
                long nowMs = SystemClock.elapsedRealtime();
                boolean steady = steadySampling.get() && nowMs >= steadyStartElapsedMs.get();
                if (steady && steadySamplerCpuStartNs < 0L) {
                    steadySamplerCpuStartNs = Debug.threadCpuTimeNanos();
                }
                sampleProcess(counters, steady);
                SystemClock.sleep(SAMPLE_INTERVAL_MS);
            }
            sampleProcess(counters, false);
            counters.samplerCpuMs.set(Math.max(0L, (Debug.threadCpuTimeNanos() - samplerCpuStartNs) / 1_000_000L));
            if (steadySamplerCpuStartNs >= 0L) {
                counters.steadySamplerCpuMs.set(Math.max(0L, (Debug.threadCpuTimeNanos() - steadySamplerCpuStartNs) / 1_000_000L));
            }
        }, "formal-benchmark-sampler");
        sampler.setDaemon(true);
        sampler.start();

        long cpuStartMs = android.os.Process.getElapsedCpuTime();
        long wallStartMs = SystemClock.elapsedRealtime();
        long startNs = SystemClock.elapsedRealtimeNanos();
        long endNs = startNs + (DEFAULT_DURATION_S * NS_PER_SECOND);
        long steadyStartOffsetMs = Math.max(0L, (DEFAULT_DURATION_S - DEFAULT_STEADY_STATE_DURATION_S) * 1000L);
        long steadyStartNs = startNs + steadyStartOffsetMs * 1_000_000L;
        steadyStartElapsedMs.set(wallStartMs + steadyStartOffsetMs);
        long nextEmitNs = startNs;
        long intervalNs = Math.max(1L, NS_PER_SECOND / scenario.targetLps);

        try {
            while (!cancelled.get()) {
                long nowNs = SystemClock.elapsedRealtimeNanos();
                if (nowNs >= endNs) {
                    break;
                }
                markSteadyStart(counters, nowNs, steadyStartNs);
                if (nextEmitNs > nowNs) {
                    sleepNs(nextEmitNs - nowNs);
                }
                long enqueueStartNs = SystemClock.elapsedRealtimeNanos();
                counters.attemptedLogs.incrementAndGet();
                try {
                    long buildStartNs = SystemClock.elapsedRealtimeNanos();
                    Log log = buildLog(scenario.profile, counters.attemptedLogs.get());
                    counters.buildLogWallNs.addAndGet(SystemClock.elapsedRealtimeNanos() - buildStartNs);

                    long addStartNs = SystemClock.elapsedRealtimeNanos();
                    client.addLog(log);
                    counters.addLogWallNs.addAndGet(SystemClock.elapsedRealtimeNanos() - addStartNs);
                    counters.acceptedLogs.incrementAndGet();
                } catch (Exception e) {
                    counters.rejectedLogs.incrementAndGet();
                    counters.lastFailure.set(sanitize(String.valueOf(e)));
                } finally {
                    long enqueueNs = SystemClock.elapsedRealtimeNanos() - enqueueStartNs;
                    counters.enqueueTimeNs.addAndGet(enqueueNs);
                    updateMax(counters.enqueueMaxNs, enqueueNs);
                }
                nextEmitNs += intervalNs;
            }
        } finally {
            long enqueueEndMs = SystemClock.elapsedRealtime();
            markSteadyStart(counters, SystemClock.elapsedRealtimeNanos(), steadyStartNs);
            long processCpuEndMs = android.os.Process.getElapsedCpuTime();
            if (counters.steadyStarted.get()) {
                counters.steadyWallMs.set(Math.max(1L, enqueueEndMs - counters.steadyWallStartMs.get()));
                counters.steadyProcessCpuMs.set(Math.max(0L, processCpuEndMs - counters.steadyProcessCpuStartMs.get()));
            }
            steadySampling.set(false);
            client.destroyLogProducer();
            long destroyStartMs = SystemClock.elapsedRealtime();
            boolean destroyCompleted = client.awaitDestroy(DEFAULT_DESTROY_AWAIT_MS);
            long destroyEndMs = SystemClock.elapsedRealtime();
            counters.destroyCompleted.set(destroyCompleted);
            counters.enqueueWallMs.set(enqueueEndMs - wallStartMs);
            counters.destroyWallMs.set(destroyEndMs - destroyStartMs);
            counters.wallTotalMs.set(destroyEndMs - wallStartMs);
            counters.processCpuMs.set(android.os.Process.getElapsedCpuTime() - cpuStartMs);
            sampling.set(false);
            sampler.join(5000L);
            JniNativeProducerBridge.AddLogPerfStatsSnapshot addLogPerfStats = JniNativeProducerBridge.snapshotAddLogPerfStats();
            JniNativeProducerBridge.SenderPerfStatsSnapshot senderPerfStats = JniNativeProducerBridge.snapshotSenderPerfStats();
            counters.javaFlattenWallMs.set(addLogPerfStats.getJavaFlattenWallMs());
            counters.nativeAddLogWallMs.set(addLogPerfStats.getNativeAddLogWallMs());
            counters.nativeUtfDupWallMs.set(addLogPerfStats.getNativeUtfDupWallMs());
            counters.nativeProducerAddLogWallMs.set(addLogPerfStats.getNativeProducerAddLogWallMs());
            counters.nativeKeyLensWallMs.set(addLogPerfStats.getNativeKeyLensWallMs());
            counters.nativePersistentPathWallMs.set(addLogPerfStats.getNativePersistentPathWallMs());
            counters.nativeQueuePathWallMs.set(addLogPerfStats.getNativeQueuePathWallMs());
            counters.nativeTlsBatchBuilderWallMs.set(addLogPerfStats.getNativeTlsBatchBuilderWallMs());
            counters.nativeTlsBatchFlushWallMs.set(addLogPerfStats.getNativeTlsBatchFlushWallMs());
            counters.nativeTlsBatchMergeWallMs.set(addLogPerfStats.getNativeTlsBatchMergeWallMs());
            counters.nativePersistentBuilderWallMs.set(addLogPerfStats.getNativePersistentBuilderWallMs());
            counters.nativePersistentAppendWallMs.set(addLogPerfStats.getNativePersistentAppendWallMs());
            counters.nativePersistentEnqueueWallMs.set(addLogPerfStats.getNativePersistentEnqueueWallMs());
            counters.nativePersistentEnqueueWaitBufferWallMs.set(addLogPerfStats.getNativePersistentEnqueueWaitBufferWallMs());
            counters.nativePersistentEnqueueBuilderInitWallMs.set(addLogPerfStats.getNativePersistentEnqueueBuilderInitWallMs());
            counters.nativePersistentEnqueueIngressPushWallMs.set(addLogPerfStats.getNativePersistentEnqueueIngressPushWallMs());
            counters.nativePersistentEnqueueIngressWaitQueueWallMs.set(addLogPerfStats.getNativePersistentEnqueueIngressWaitQueueWallMs());
            counters.nativePersistentEnqueueIngressBookkeepingWallMs.set(addLogPerfStats.getNativePersistentEnqueueIngressBookkeepingWallMs());
            counters.nativePersistentEnqueueIngressNotifyWallMs.set(addLogPerfStats.getNativePersistentEnqueueIngressNotifyWallMs());
            counters.nativeTlsBatchPreambleWallMs.set(addLogPerfStats.getNativeTlsBatchPreambleWallMs());
            counters.nativeTlsBatchBookkeepingWallMs.set(addLogPerfStats.getNativeTlsBatchBookkeepingWallMs());
            counters.nativePersistentAppendEncodeWallMs.set(addLogPerfStats.getNativePersistentAppendEncodeWallMs());
            counters.nativePersistentAppendStoreWallMs.set(addLogPerfStats.getNativePersistentAppendStoreWallMs());
            counters.nativeTlsBatchFlushShellWallMs.set(addLogPerfStats.getNativeTlsBatchFlushShellWallMs());
            counters.nativeTlsBatchMergeShellWallMs.set(addLogPerfStats.getNativeTlsBatchMergeShellWallMs());
            counters.nativePersistentAppendSizingWallMs.set(addLogPerfStats.getNativePersistentAppendSizingWallMs());
            counters.nativePersistentAppendCapacityWallMs.set(addLogPerfStats.getNativePersistentAppendCapacityWallMs());
            counters.nativePersistentAppendPostStoreWallMs.set(addLogPerfStats.getNativePersistentAppendPostStoreWallMs());
            counters.nativePersistentAppendRetryShellWallMs.set(addLogPerfStats.getNativePersistentAppendRetryShellWallMs());
            counters.nativePersistentAppendPrecheckWallMs.set(addLogPerfStats.getNativePersistentAppendPrecheckWallMs());
            counters.nativePersistentAppendMetaWallMs.set(addLogPerfStats.getNativePersistentAppendMetaWallMs());
            counters.nativePersistentAppendRetryPersistentMutexWallMs.set(addLogPerfStats.getNativePersistentAppendRetryPersistentMutexWallMs());
            counters.nativePersistentAppendRetrySleepWallMs.set(addLogPerfStats.getNativePersistentAppendRetrySleepWallMs());
            counters.nativePersistentAppendRetryProducerMutexWallMs.set(addLogPerfStats.getNativePersistentAppendRetryProducerMutexWallMs());
            counters.nativePersistentAppendStoreRotateWallMs.set(addLogPerfStats.getNativePersistentAppendStoreRotateWallMs());
            counters.nativePersistentAppendStoreWriteWallMs.set(addLogPerfStats.getNativePersistentAppendStoreWriteWallMs());
            counters.nativePersistentAppendRetryProducerLockWallMs.set(addLogPerfStats.getNativePersistentAppendRetryProducerLockWallMs());
            counters.nativePersistentAppendRetryProducerNotifyWallMs.set(addLogPerfStats.getNativePersistentAppendRetryProducerNotifyWallMs());
            counters.sendCount.set(senderPerfStats.getSendCount());
            counters.sendWallMs.set(senderPerfStats.getSendWallMs());
            counters.signWallMs.set(senderPerfStats.getSignWallMs());
            counters.httpRequestWallMs.set(senderPerfStats.getHttpRequestWallMs());
            counters.managerTaskCount.set(senderPerfStats.getManagerTaskCount());
            counters.managerBuildTaskWallMs.set(senderPerfStats.getManagerBuildTaskWallMs());
            counters.managerPrepareTaskWallMs.set(senderPerfStats.getManagerPrepareTaskWallMs());
            counters.managerCompressWallMs.set(senderPerfStats.getManagerCompressWallMs());
            counters.managerPushTaskWallMs.set(senderPerfStats.getManagerPushTaskWallMs());
        }

        return buildReport(context, runId, scenario, counters);
    }

    private static Log buildLog(String profileName, long index) {
        Log log = new Log().setLogTime(System.currentTimeMillis());
        for (String[] entry : BenchmarkPayload.entries(profileName, index)) {
            log.putContent(entry[0], entry[1]);
        }
        return log;
    }

    private static LogProducerConfig buildConfig(Context context, Properties props, String runId, FormalBenchmarkPlan.Scenario scenario) {
        LogProducerConfig.CompressType compressType = parseCompressType(ConfigLoader.get(props, "compress"));
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint(require(ConfigLoader.get(props, "endPoint"), "endPoint"))
                .setRegion(require(ConfigLoader.get(props, "region"), "region"))
                .setAccessKeyId(require(ConfigLoader.get(props, "ak"), "ak"))
                .setAccessKeySecret(require(ConfigLoader.get(props, "sk"), "sk"))
                .setSecurityToken(defaultString(ConfigLoader.get(props, "token")))
                .setTopicId(require(ConfigLoader.get(props, "topicId"), "topicId"))
                .setCompressType(compressType)
                .setSendThreadCount(DEFAULT_SEND_THREAD_COUNT)
                .setRetryMaxAttempts(DEFAULT_RETRY_MAX_ATTEMPTS)
                .setRetryTotalTimeoutMs(DEFAULT_RETRY_TOTAL_TIMEOUT_MS)
                .setRetryInitialIntervalMs(DEFAULT_RETRY_INITIAL_INTERVAL_MS)
                .setRetryMaxIntervalMs(DEFAULT_RETRY_MAX_INTERVAL_MS)
                .setConnectTimeoutMs(DEFAULT_CONNECT_TIMEOUT_MS)
                .setRequestTimeoutMs(DEFAULT_REQUEST_TIMEOUT_MS)
                .setPacketTimeoutMs(DEFAULT_PACKET_TIMEOUT_MS)
                .setPacketLogBytes(DEFAULT_PACKET_LOG_BYTES)
                .setPacketLogCount(DEFAULT_PACKET_LOG_COUNT)
                .setMaxBufferLimit(DEFAULT_MAX_BUFFER_LIMIT)
                .setDestroyWaitMs((int) DEFAULT_DESTROY_AWAIT_MS)
                .setCallbackFromSenderThread(false);
        if ("persistent".equals(scenario.mode)) {
            File path = persistentPath(context, runId, scenario);
            if (!path.exists() && !path.mkdirs()) {
                throw new IllegalStateException("failed to create persistent dir: " + path.getAbsolutePath());
            }
            config.setPersistent(true)
                    .setPersistentFilePath(path.getAbsolutePath())
                    .setPersistentMaxFileCount(DEFAULT_PERSISTENT_MAX_FILE_COUNT)
                    .setPersistentMaxFileSize(DEFAULT_PERSISTENT_MAX_FILE_SIZE)
                    .setPersistentMaxLogCount(DEFAULT_PERSISTENT_MAX_LOG_COUNT);
        }
        return config;
    }

    private static File persistentPath(Context context, String runId, FormalBenchmarkPlan.Scenario scenario) {
        return new File(new File(context.getFilesDir(), "formal-benchmark-persistent"), runId + "/" + scenario.scenarioId());
    }

    private static FormalBenchmarkReport buildReport(Context context, String runId, FormalBenchmarkPlan.Scenario scenario, BenchmarkCounters counters) {
        long enqueueWallMs = Math.max(1L, counters.enqueueWallMs.get());
        long wallTotalMs = Math.max(1L, counters.wallTotalMs.get());
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        FormalBenchmarkMath.CpuMetrics cpuMetrics = FormalBenchmarkMath.cpuMetrics(
                counters.processCpuMs.get(),
                counters.samplerCpuMs.get(),
                wallTotalMs,
                availableProcessors);
        long steadyWallMs = Math.max(1L, counters.steadyWallMs.get());
        FormalBenchmarkMath.CpuMetrics steadyCpuMetrics = FormalBenchmarkMath.cpuMetrics(
                counters.steadyProcessCpuMs.get(),
                counters.steadySamplerCpuMs.get(),
                steadyWallMs,
                availableProcessors);
        double acceptedLps = (counters.acceptedLogs.get() * 1000.0) / enqueueWallMs;
        double rawKbPerSec = counters.callbackLogBytes.get() / 1024.0 / (wallTotalMs / 1000.0);
        double compressedKbPerSec = counters.callbackCompressedBytes.get() / 1024.0 / (wallTotalMs / 1000.0);
        String status = FormalBenchmarkMath.status(
                counters.destroyCompleted.get(),
                counters.acceptedLogs.get(),
                counters.callbackSuccess.get(),
                counters.callbackFailure.get(),
                counters.rejectedLogs.get());
        return new FormalBenchmarkReport(
                scenario.mode,
                scenario.profile,
                scenario.targetLps,
                status,
                acceptedLps,
                counters.acceptedLogs.get(),
                counters.rejectedLogs.get(),
                counters.callbackSuccess.get(),
                counters.callbackFailure.get(),
                counters.destroyCompleted.get(),
                cpuMetrics.cpuMs,
                nanosToMillis(counters.buildLogWallNs.get()),
                nanosToMillis(counters.addLogWallNs.get()),
                counters.javaFlattenWallMs.get(),
                counters.nativeAddLogWallMs.get(),
                counters.nativeUtfDupWallMs.get(),
                counters.nativeProducerAddLogWallMs.get(),
                counters.nativeKeyLensWallMs.get(),
                counters.nativePersistentPathWallMs.get(),
                counters.nativeQueuePathWallMs.get(),
                counters.nativeTlsBatchBuilderWallMs.get(),
                counters.nativeTlsBatchFlushWallMs.get(),
                counters.nativeTlsBatchMergeWallMs.get(),
                counters.nativePersistentBuilderWallMs.get(),
                counters.nativePersistentAppendWallMs.get(),
                counters.nativePersistentEnqueueWallMs.get(),
                counters.nativePersistentEnqueueWaitBufferWallMs.get(),
                counters.nativePersistentEnqueueBuilderInitWallMs.get(),
                counters.nativePersistentEnqueueIngressPushWallMs.get(),
                counters.nativePersistentEnqueueIngressWaitQueueWallMs.get(),
                counters.nativePersistentEnqueueIngressBookkeepingWallMs.get(),
                counters.nativePersistentEnqueueIngressNotifyWallMs.get(),
                counters.nativeTlsBatchPreambleWallMs.get(),
                counters.nativeTlsBatchBookkeepingWallMs.get(),
                counters.nativePersistentAppendEncodeWallMs.get(),
                counters.nativePersistentAppendStoreWallMs.get(),
                counters.nativeTlsBatchFlushShellWallMs.get(),
                counters.nativeTlsBatchMergeShellWallMs.get(),
                counters.nativePersistentAppendSizingWallMs.get(),
                counters.nativePersistentAppendCapacityWallMs.get(),
                counters.nativePersistentAppendPostStoreWallMs.get(),
                counters.nativePersistentAppendRetryShellWallMs.get(),
                counters.nativePersistentAppendPrecheckWallMs.get(),
                counters.nativePersistentAppendMetaWallMs.get(),
                counters.nativePersistentAppendRetryPersistentMutexWallMs.get(),
                counters.nativePersistentAppendRetrySleepWallMs.get(),
                counters.nativePersistentAppendRetryProducerMutexWallMs.get(),
                counters.nativePersistentAppendStoreRotateWallMs.get(),
                counters.nativePersistentAppendStoreWriteWallMs.get(),
                counters.nativePersistentAppendRetryProducerLockWallMs.get(),
                counters.nativePersistentAppendRetryProducerNotifyWallMs.get(),
                counters.sendCount.get(),
                counters.sendWallMs.get(),
                counters.signWallMs.get(),
                counters.httpRequestWallMs.get(),
                counters.managerTaskCount.get(),
                counters.managerBuildTaskWallMs.get(),
                counters.managerPrepareTaskWallMs.get(),
                counters.managerCompressWallMs.get(),
                counters.managerPushTaskWallMs.get(),
                counters.enqueueWallMs.get(),
                counters.destroyWallMs.get(),
                counters.steadyWallMs.get(),
                steadyCpuMetrics.cpuMs,
                steadyCpuMetrics.cpuPctTotal,
                counters.steadyPssPeakKb.get(),
                counters.steadyRssPeakKb.get(),
                counters.steadyThreadsPeak.get(),
                cpuMetrics.cpuPctTotal,
                wallTotalMs,
                counters.pssPeakKb.get(),
                counters.rssPeakKb.get(),
                counters.threadsPeak.get(),
                availableProcessors,
                rawKbPerSec,
                compressedKbPerSec,
                counters.lastFailure.get());
    }

    private static void handleCompletion(LogProducerResult result, BenchmarkCounters counters) {
        if (result == null) {
            counters.callbackFailure.incrementAndGet();
            counters.lastFailure.set("callback result=null");
            return;
        }
        counters.callbackLogBytes.addAndGet(Math.max(0L, result.getLogBytes()));
        counters.callbackCompressedBytes.addAndGet(Math.max(0L, result.getCompressedBytes()));
        if (result.isSuccess()) {
            counters.callbackSuccess.incrementAndGet();
        } else {
            counters.callbackFailure.incrementAndGet();
            counters.lastFailure.set(sanitize(result.getFailureSummary()));
        }
    }

    private static void markSteadyStart(BenchmarkCounters counters, long nowNs, long steadyStartNs) {
        if (nowNs < steadyStartNs || !counters.steadyStarted.compareAndSet(false, true)) {
            return;
        }
        counters.steadyWallStartMs.set(SystemClock.elapsedRealtime());
        counters.steadyProcessCpuStartMs.set(android.os.Process.getElapsedCpuTime());
    }

    private static void sampleProcess(BenchmarkCounters counters, boolean steady) {
        StatusSample statusSample = readStatusSample();
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);

        if (statusSample.vmrssKb > 0) {
            updateMax(counters.rssPeakKb, statusSample.vmrssKb);
            if (steady) {
                updateMax(counters.steadyRssPeakKb, statusSample.vmrssKb);
            }
        }
        if (statusSample.vmhwmKb > 0) {
            updateMax(counters.rssPeakKb, statusSample.vmhwmKb);
            if (steady) {
                updateMax(counters.steadyRssPeakKb, statusSample.vmhwmKb);
            }
        }
        if (statusSample.threads > 0) {
            updateMax(counters.threadsPeak, statusSample.threads);
            if (steady) {
                updateMax(counters.steadyThreadsPeak, statusSample.threads);
            }
        }
        updateMax(counters.pssPeakKb, memoryInfo.getTotalPss());
        if (steady) {
            updateMax(counters.steadyPssPeakKb, memoryInfo.getTotalPss());
        }
    }

    private static StatusSample readStatusSample() {
        StatusSample sample = new StatusSample();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    sample.vmrssKb = parseStatusKb(line);
                } else if (line.startsWith("VmHWM:")) {
                    sample.vmhwmKb = parseStatusKb(line);
                } else if (line.startsWith("Threads:")) {
                    sample.threads = parseStatusValue(line);
                }
            }
        } catch (IOException ignored) {
        }
        return sample;
    }

    private static long parseStatusKb(String line) {
        return parseStatusValue(line.replace("kB", ""));
    }

    private static long parseStatusValue(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) {
            return -1L;
        }
        try {
            return Long.parseLong(parts[1].trim().split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static void sleepNs(long sleepNs) {
        if (sleepNs <= 0) {
            return;
        }
        long sleepMs = sleepNs / 1_000_000L;
        int sleepExtraNs = (int) (sleepNs % 1_000_000L);
        try {
            Thread.sleep(sleepMs, sleepExtraNs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeProperties(File file, Properties properties) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            properties.store(stream, "producer-native formal benchmark");
        }
    }

    private static void writeString(File file, String body) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.flush();
        }
    }

    private static LogProducerConfig.CompressType parseCompressType(String value) {
        String normalized = ConfigLoader.normalizeCompressValue(value);
        if ("none".equals(normalized)) {
            return LogProducerConfig.CompressType.NONE;
        }
        return LogProducerConfig.CompressType.LZ4;
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing config: " + name);
        }
        return value.trim();
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static long nanosToMillis(long value) {
        return Math.max(0L, value / 1_000_000L);
    }

    private static void updateMax(AtomicLong target, long value) {
        while (true) {
            long current = target.get();
            if (value <= current) {
                return;
            }
            if (target.compareAndSet(current, value)) {
                return;
            }
        }
    }

    private static final class StatusSample {
        long vmrssKb;
        long vmhwmKb;
        long threads;
    }

    private static final class BenchmarkCounters {
        final AtomicLong attemptedLogs = new AtomicLong();
        final AtomicLong acceptedLogs = new AtomicLong();
        final AtomicLong rejectedLogs = new AtomicLong();
        final AtomicLong callbackSuccess = new AtomicLong();
        final AtomicLong callbackFailure = new AtomicLong();
        final AtomicLong callbackLogBytes = new AtomicLong();
        final AtomicLong callbackCompressedBytes = new AtomicLong();
        final AtomicLong buildLogWallNs = new AtomicLong();
        final AtomicLong addLogWallNs = new AtomicLong();
        final AtomicLong javaFlattenWallMs = new AtomicLong();
        final AtomicLong nativeAddLogWallMs = new AtomicLong();
        final AtomicLong nativeUtfDupWallMs = new AtomicLong();
        final AtomicLong nativeProducerAddLogWallMs = new AtomicLong();
        final AtomicLong nativeKeyLensWallMs = new AtomicLong();
        final AtomicLong nativePersistentPathWallMs = new AtomicLong();
        final AtomicLong nativeQueuePathWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchBuilderWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchFlushWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchMergeWallMs = new AtomicLong();
        final AtomicLong nativePersistentBuilderWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueWaitBufferWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueBuilderInitWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueIngressPushWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueIngressWaitQueueWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueIngressBookkeepingWallMs = new AtomicLong();
        final AtomicLong nativePersistentEnqueueIngressNotifyWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchPreambleWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchBookkeepingWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendEncodeWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendStoreWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchFlushShellWallMs = new AtomicLong();
        final AtomicLong nativeTlsBatchMergeShellWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendSizingWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendCapacityWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendPostStoreWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendRetryShellWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendPrecheckWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendMetaWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendRetryPersistentMutexWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendRetrySleepWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendRetryProducerMutexWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendStoreRotateWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendStoreWriteWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendRetryProducerLockWallMs = new AtomicLong();
        final AtomicLong nativePersistentAppendRetryProducerNotifyWallMs = new AtomicLong();
        final AtomicLong sendCount = new AtomicLong();
        final AtomicLong sendWallMs = new AtomicLong();
        final AtomicLong signWallMs = new AtomicLong();
        final AtomicLong httpRequestWallMs = new AtomicLong();
        final AtomicLong managerTaskCount = new AtomicLong();
        final AtomicLong managerBuildTaskWallMs = new AtomicLong();
        final AtomicLong managerPrepareTaskWallMs = new AtomicLong();
        final AtomicLong managerCompressWallMs = new AtomicLong();
        final AtomicLong managerPushTaskWallMs = new AtomicLong();
        final AtomicLong enqueueTimeNs = new AtomicLong();
        final AtomicLong enqueueMaxNs = new AtomicLong();
        final AtomicLong enqueueWallMs = new AtomicLong();
        final AtomicLong destroyWallMs = new AtomicLong();
        final AtomicLong wallTotalMs = new AtomicLong();
        final AtomicLong processCpuMs = new AtomicLong();
        final AtomicLong samplerCpuMs = new AtomicLong();
        final AtomicBoolean steadyStarted = new AtomicBoolean();
        final AtomicLong steadyWallStartMs = new AtomicLong();
        final AtomicLong steadyWallMs = new AtomicLong();
        final AtomicLong steadyProcessCpuStartMs = new AtomicLong();
        final AtomicLong steadyProcessCpuMs = new AtomicLong();
        final AtomicLong steadySamplerCpuMs = new AtomicLong();
        final AtomicLong pssPeakKb = new AtomicLong();
        final AtomicLong rssPeakKb = new AtomicLong();
        final AtomicLong threadsPeak = new AtomicLong();
        final AtomicLong steadyPssPeakKb = new AtomicLong();
        final AtomicLong steadyRssPeakKb = new AtomicLong();
        final AtomicLong steadyThreadsPeak = new AtomicLong();
        final AtomicBoolean destroyCompleted = new AtomicBoolean();
        final AtomicReference<String> lastFailure = new AtomicReference<>("");
    }
}
