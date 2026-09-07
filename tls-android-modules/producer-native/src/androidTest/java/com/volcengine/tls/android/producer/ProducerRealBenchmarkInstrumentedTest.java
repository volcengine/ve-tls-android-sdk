package com.volcengine.tls.android.producer;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ProducerRealBenchmarkInstrumentedTest extends TestCase {
    private static final long NS_PER_SECOND = 1_000_000_000L;
    private static final long SAMPLE_INTERVAL_MS = 200L;
    private static final int STEADY_STATE_DURATION_S = 60;

    private static final String[][] FIXED_TAGS = new String[][] {
            {"tag_1", "val_1"},
            {"tag_2", "val_2"},
            {"tag_3", "val_3"},
            {"tag_4", "val_4"},
            {"tag_5", "val_5"}
    };

    public void testRunRealBenchmarkScenario() throws Exception {
        Properties props = loadConfig();
        ProducerRealBenchmarkScenario scenario = ProducerRealBenchmarkScenario.from(props, InstrumentationRegistry.getArguments());
        BenchmarkReport report = runBenchmark(scenario, props);
        File reportFile = writeReport(report);
        System.out.println("BENCHMARK_REPORT path=" + reportFile.getAbsolutePath()
                + " status=" + report.status
                + " profile=" + report.profile
                + " rate_lps=" + report.targetLps
                + " accepted=" + report.acceptedLogs
                + " successful_logs=" + report.successfulLogs
                + " remaining_logs=" + report.remainingLogs
                + " invalid_success_ranges=" + report.invalidSuccessRanges
                + " callback_ok=" + report.callbackSuccess
                + " callback_fail=" + report.callbackFailure);

        assertTrue("benchmark accepted no logs", report.acceptedLogs > 0);
        assertTrue("benchmark destroy did not complete", report.destroyCompleted);
        assertTrue("benchmark callbacks never arrived", (report.callbackSuccess + report.callbackFailure) > 0);
        assertTrue("benchmark status not acceptable: " + report.status,
                ProducerRealBenchmarkMath.isAcceptableStatus(report.status, scenario.failOnDegraded));
    }

    private BenchmarkReport runBenchmark(ProducerRealBenchmarkScenario scenario, Properties props) throws Exception {
        BenchmarkCounters counters = new BenchmarkCounters();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        LogProducerConfig config = scenario.buildConfig(props, context);
        for (String[] tag : FIXED_TAGS) {
            config.addTag(tag[0], tag[1]);
        }
        BenchmarkPayload.warmUp();
        LogProducerClient client = new LogProducerClient(config, result -> {
            if (result == null) {
                counters.callbackFailure.incrementAndGet();
                counters.lastFailure.set("callback result=null");
                return;
            }
            counters.callbackLogBytes.addAndGet(Math.max(0L, result.getLogBytes()));
            counters.callbackCompressedBytes.addAndGet(Math.max(0L, result.getCompressedBytes()));
            if (result.isSuccess()) {
                counters.callbackSuccess.incrementAndGet();
                counters.delivery.recordSuccessRange(result.getStartId(), result.getEndId());
            } else {
                counters.callbackFailure.incrementAndGet();
                counters.lastFailure.set(sanitize(result.getFailureSummary()));
            }
        });

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
            counters.samplerCpuMs.set(Math.max(0L, (Debug.threadCpuTimeNanos() - samplerCpuStartNs) / 1000000L));
            if (steadySamplerCpuStartNs >= 0L) {
                counters.steadySamplerCpuMs.set(Math.max(0L, (Debug.threadCpuTimeNanos() - steadySamplerCpuStartNs) / 1000000L));
            }
        }, "producer-real-benchmark-sampler");
        sampler.setDaemon(true);
        sampler.start();

        long cpuStartMs = android.os.Process.getElapsedCpuTime();
        long wallStartMs = SystemClock.elapsedRealtime();
        long startNs = SystemClock.elapsedRealtimeNanos();
        long endNs = startNs + (scenario.durationS * NS_PER_SECOND);
        long steadyStartOffsetMs = Math.max(0L, (scenario.durationS - STEADY_STATE_DURATION_S) * 1000L);
        long steadyStartNs = startNs + steadyStartOffsetMs * 1000000L;
        steadyStartElapsedMs.set(wallStartMs + steadyStartOffsetMs);
        long nextEmitNs = startNs;
        long intervalNs = scenario.rateLps > 0 ? Math.max(1L, NS_PER_SECOND / scenario.rateLps) : 0L;

        try {
            while (true) {
                long nowNs = SystemClock.elapsedRealtimeNanos();
                if (nowNs >= endNs) {
                    break;
                }
                markSteadyStart(counters, nowNs, steadyStartNs);
                if (intervalNs > 0 && nextEmitNs > nowNs) {
                    sleepNs(nextEmitNs - nowNs);
                }
                long enqueueStartNs = SystemClock.elapsedRealtimeNanos();
                counters.attemptedLogs.incrementAndGet();
                try {
                    client.addLog(buildLog(scenario.profileName, counters.attemptedLogs.get()));
                    counters.acceptedLogs.incrementAndGet();
                } catch (Exception e) {
                    counters.rejectedLogs.incrementAndGet();
                    counters.lastFailure.set(sanitize(String.valueOf(e)));
                } finally {
                    long enqueueNs = SystemClock.elapsedRealtimeNanos() - enqueueStartNs;
                    counters.enqueueTimeNs.addAndGet(enqueueNs);
                    updateMax(counters.enqueueMaxNs, enqueueNs);
                }
                if (intervalNs > 0) {
                    nextEmitNs += intervalNs;
                }
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
            boolean destroyCompleted = client.awaitDestroy(scenario.destroyAwaitMs);
            long destroyEndMs = SystemClock.elapsedRealtime();
            counters.destroyCompleted.set(destroyCompleted);
            counters.enqueueWallMs.set(enqueueEndMs - wallStartMs);
            counters.destroyWallMs.set(destroyEndMs - destroyStartMs);
            counters.wallTotalMs.set(destroyEndMs - wallStartMs);
            counters.processCpuMs.set(android.os.Process.getElapsedCpuTime() - cpuStartMs);
            sampling.set(false);
            sampler.join(5000);
        }

        return BenchmarkReport.from(scenario, counters, context);
    }

    private static Log buildLog(String profileName, long index) {
        Log log = new Log().setLogTime(System.currentTimeMillis());
        for (String[] entry : BenchmarkPayload.entries(profileName, index)) {
            log.putContent(entry[0], entry[1]);
        }
        return log;
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
            counters.rssCurrentKb.set(statusSample.vmrssKb);
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
        updateMax(counters.nativePssPeakKb, memoryInfo.nativePss);
        updateMax(counters.dalvikPssPeakKb, memoryInfo.dalvikPss);
        long heapUsedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L;
        updateMax(counters.javaHeapPeakKb, heapUsedKb);
    }

    private File writeReport(BenchmarkReport report) throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        File dir = context.getExternalFilesDir("benchmark");
        assertNotNull("benchmark external files dir missing", dir);
        if (!dir.exists()) {
            assertTrue("failed to create benchmark dir", dir.mkdirs());
        }
        File reportFile = new File(dir, report.runId + ".properties");
        Properties output = report.toProperties();
        try (FileOutputStream stream = new FileOutputStream(reportFile)) {
            output.store(stream, "producer-native real benchmark");
        }
        return reportFile;
    }

    private Properties loadConfig() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
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
        long sleepMs = sleepNs / 1000000L;
        int nanos = (int) (sleepNs % 1000000L);
        try {
            Thread.sleep(sleepMs, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void updateMax(AtomicLong target, long value) {
        if (value < 0) {
            return;
        }
        for (;;) {
            long current = target.get();
            if (value <= current) {
                return;
            }
            if (target.compareAndSet(current, value)) {
                return;
            }
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final class StatusSample {
        long vmrssKb = -1L;
        long vmhwmKb = -1L;
        long threads = -1L;
    }

    private static final class BenchmarkCounters {
        final AtomicLong attemptedLogs = new AtomicLong();
        final AtomicLong acceptedLogs = new AtomicLong();
        final AtomicLong rejectedLogs = new AtomicLong();
        final AtomicLong callbackSuccess = new AtomicLong();
        final AtomicLong callbackFailure = new AtomicLong();
        final AtomicLong callbackLogBytes = new AtomicLong();
        final AtomicLong callbackCompressedBytes = new AtomicLong();
        final ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
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
        final AtomicLong nativePssPeakKb = new AtomicLong();
        final AtomicLong dalvikPssPeakKb = new AtomicLong();
        final AtomicLong rssPeakKb = new AtomicLong();
        final AtomicLong rssCurrentKb = new AtomicLong();
        final AtomicLong threadsPeak = new AtomicLong();
        final AtomicLong steadyPssPeakKb = new AtomicLong();
        final AtomicLong steadyRssPeakKb = new AtomicLong();
        final AtomicLong steadyThreadsPeak = new AtomicLong();
        final AtomicLong javaHeapPeakKb = new AtomicLong();
        final AtomicBoolean destroyCompleted = new AtomicBoolean();
        final AtomicReference<String> lastFailure = new AtomicReference<>("");
    }

    private static final class BenchmarkReport {
        final String status;
        final String runId;
        final String profile;
        final long targetLps;
        final int durationS;
        final String compress;
        final int sendThreadCount;
        final boolean callbackFromSenderThread;
        final boolean persistent;
        final int packetLogBytes;
        final int packetLogCount;
        final int packetTimeoutMs;
        final int maxBufferLimit;
        final int persistentMaxFileCount;
        final int persistentMaxFileSize;
        final int persistentMaxLogCount;
        final String persistentFilePath;
        final long attemptedLogs;
        final long acceptedLogs;
        final long rejectedLogs;
        final long callbackSuccess;
        final long callbackFailure;
        final long successfulLogs;
        final long remainingLogs;
        final long invalidSuccessRanges;
        final long callbackLogBytes;
        final long callbackCompressedBytes;
        final long enqueueWallMs;
        final long destroyWallMs;
        final long wallTotalMs;
        final long steadyWallMs;
        final long steadyProcessCpuMs;
        final long steadySamplerCpuMs;
        final long steadyCpuMs;
        final long processCpuMs;
        final long samplerCpuMs;
        final long cpuMs;
        final int availableProcessors;
        final double steadyCpuPct1;
        final double steadyCpuPctTotal;
        final double processCpuPct1;
        final double processCpuPctTotal;
        final double samplerCpuPct1;
        final double samplerCpuPctTotal;
        final double cpuPct1;
        final double cpuPctTotal;
        final long pssPeakKb;
        final long nativePssPeakKb;
        final long dalvikPssPeakKb;
        final long rssPeakKb;
        final long rssCurrentKb;
        final long threadsPeak;
        final long steadyPssPeakKb;
        final long steadyRssPeakKb;
        final long steadyThreadsPeak;
        final long javaHeapPeakKb;
        final long enqueueAvgUs;
        final long enqueueMaxUs;
        final double acceptedLps;
        final double callbackRawKbPerSec;
        final double callbackCompressedKbPerSec;
        final double compressionRatio;
        final boolean destroyCompleted;
        final String deviceModel;
        final int deviceSdk;
        final String deviceAbi;
        final String lastFailure;

        private BenchmarkReport(
                String status,
                ProducerRealBenchmarkScenario scenario,
                BenchmarkCounters counters,
                ProducerRealBenchmarkDelivery.Summary delivery,
                ProducerRealBenchmarkMath.CpuMetrics cpuMetrics,
                ProducerRealBenchmarkMath.CpuMetrics steadyCpuMetrics,
                long enqueueAvgUs,
                long enqueueMaxUs,
                double acceptedLps,
                double callbackRawKbPerSec,
                double callbackCompressedKbPerSec,
                int availableProcessors,
                String persistentFilePath,
                String deviceModel,
                int deviceSdk,
                String deviceAbi) {
            this.status = status;
            this.runId = scenario.runId;
            this.profile = scenario.profileName;
            this.targetLps = scenario.rateLps;
            this.durationS = scenario.durationS;
            this.compress = scenario.compressType.name().toLowerCase();
            this.sendThreadCount = scenario.effectiveSendThreadCount();
            this.callbackFromSenderThread = scenario.callbackFromSenderThread;
            this.persistent = scenario.persistent;
            this.packetLogBytes = scenario.packetLogBytes;
            this.packetLogCount = scenario.packetLogCount;
            this.packetTimeoutMs = scenario.packetTimeoutMs;
            this.maxBufferLimit = scenario.maxBufferLimit;
            this.persistentMaxFileCount = scenario.persistentMaxFileCount;
            this.persistentMaxFileSize = scenario.persistentMaxFileSize;
            this.persistentMaxLogCount = scenario.persistentMaxLogCount;
            this.persistentFilePath = persistentFilePath;
            this.attemptedLogs = counters.attemptedLogs.get();
            this.acceptedLogs = counters.acceptedLogs.get();
            this.rejectedLogs = counters.rejectedLogs.get();
            this.callbackSuccess = counters.callbackSuccess.get();
            this.callbackFailure = counters.callbackFailure.get();
            this.successfulLogs = delivery.successfulLogs;
            this.remainingLogs = delivery.remainingLogs;
            this.invalidSuccessRanges = delivery.invalidSuccessRanges;
            this.callbackLogBytes = counters.callbackLogBytes.get();
            this.callbackCompressedBytes = counters.callbackCompressedBytes.get();
            this.enqueueWallMs = counters.enqueueWallMs.get();
            this.destroyWallMs = counters.destroyWallMs.get();
            this.wallTotalMs = counters.wallTotalMs.get();
            this.steadyWallMs = counters.steadyWallMs.get();
            this.steadyProcessCpuMs = steadyCpuMetrics.processCpuMs;
            this.steadySamplerCpuMs = steadyCpuMetrics.samplerCpuMs;
            this.steadyCpuMs = steadyCpuMetrics.producerCpuMs;
            this.processCpuMs = cpuMetrics.processCpuMs;
            this.samplerCpuMs = cpuMetrics.samplerCpuMs;
            this.cpuMs = cpuMetrics.producerCpuMs;
            this.availableProcessors = availableProcessors;
            this.steadyCpuPct1 = steadyCpuMetrics.cpuPctSingleCore;
            this.steadyCpuPctTotal = steadyCpuMetrics.cpuPctTotal;
            this.processCpuPct1 = cpuMetrics.processCpuPctSingleCore;
            this.processCpuPctTotal = cpuMetrics.processCpuPctTotal;
            this.samplerCpuPct1 = cpuMetrics.samplerCpuPctSingleCore;
            this.samplerCpuPctTotal = cpuMetrics.samplerCpuPctTotal;
            this.cpuPct1 = cpuMetrics.cpuPctSingleCore;
            this.cpuPctTotal = cpuMetrics.cpuPctTotal;
            this.pssPeakKb = counters.pssPeakKb.get();
            this.nativePssPeakKb = counters.nativePssPeakKb.get();
            this.dalvikPssPeakKb = counters.dalvikPssPeakKb.get();
            this.rssPeakKb = counters.rssPeakKb.get();
            this.rssCurrentKb = counters.rssCurrentKb.get();
            this.threadsPeak = counters.threadsPeak.get();
            this.steadyPssPeakKb = counters.steadyPssPeakKb.get();
            this.steadyRssPeakKb = counters.steadyRssPeakKb.get();
            this.steadyThreadsPeak = counters.steadyThreadsPeak.get();
            this.javaHeapPeakKb = counters.javaHeapPeakKb.get();
            this.enqueueAvgUs = enqueueAvgUs;
            this.enqueueMaxUs = enqueueMaxUs;
            this.acceptedLps = acceptedLps;
            this.callbackRawKbPerSec = callbackRawKbPerSec;
            this.callbackCompressedKbPerSec = callbackCompressedKbPerSec;
            this.compressionRatio = callbackCompressedKbPerSec > 0.0 ? callbackRawKbPerSec / callbackCompressedKbPerSec : 0.0;
            this.destroyCompleted = counters.destroyCompleted.get();
            this.deviceModel = deviceModel;
            this.deviceSdk = deviceSdk;
            this.deviceAbi = deviceAbi;
            this.lastFailure = counters.lastFailure.get();
        }

        static BenchmarkReport from(ProducerRealBenchmarkScenario scenario, BenchmarkCounters counters, Context context) {
            long enqueueCount = Math.max(1L, counters.attemptedLogs.get());
            long enqueueAvgUs = (counters.enqueueTimeNs.get() / enqueueCount) / 1000L;
            long enqueueMaxUs = counters.enqueueMaxNs.get() / 1000L;
            long wallMs = Math.max(1L, counters.wallTotalMs.get());
            long enqueueWallMs = Math.max(1L, counters.enqueueWallMs.get());
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            ProducerRealBenchmarkMath.CpuMetrics cpuMetrics = ProducerRealBenchmarkMath.cpuMetrics(
                    counters.processCpuMs.get(),
                    counters.samplerCpuMs.get(),
                    wallMs,
                    availableProcessors);
            long steadyWallMs = Math.max(1L, counters.steadyWallMs.get());
            ProducerRealBenchmarkMath.CpuMetrics steadyCpuMetrics = ProducerRealBenchmarkMath.cpuMetrics(
                    counters.steadyProcessCpuMs.get(),
                    counters.steadySamplerCpuMs.get(),
                    steadyWallMs,
                    availableProcessors);
            double acceptedLps = (counters.acceptedLogs.get() * 1000.0) / enqueueWallMs;
            double callbackRawKbPerSec = counters.callbackLogBytes.get() / 1024.0 / (wallMs / 1000.0);
            double callbackCompressedKbPerSec = counters.callbackCompressedBytes.get() / 1024.0 / (wallMs / 1000.0);
            ProducerRealBenchmarkDelivery.Summary delivery = counters.delivery.snapshot(counters.acceptedLogs.get());
            String status = ProducerRealBenchmarkDelivery.statusAfterCoverage(
                    ProducerRealBenchmarkMath.status(
                            counters.destroyCompleted.get(),
                            counters.acceptedLogs.get(),
                            counters.callbackSuccess.get(),
                            counters.callbackFailure.get(),
                            counters.rejectedLogs.get()),
                    delivery);
            String persistentFilePath = scenario.persistent
                    ? scenario.persistentPath(context).getAbsolutePath()
                    : "";
            return new BenchmarkReport(
                    status,
                    scenario,
                    counters,
                    delivery,
                    cpuMetrics,
                    steadyCpuMetrics,
                    enqueueAvgUs,
                    enqueueMaxUs,
                    acceptedLps,
                    callbackRawKbPerSec,
                    callbackCompressedKbPerSec,
                    availableProcessors,
                    persistentFilePath,
                    Build.MODEL,
                    Build.VERSION.SDK_INT,
                    deviceAbi());
        }

        Properties toProperties() {
            Properties properties = new Properties();
            properties.setProperty("status", status);
            properties.setProperty("runId", runId);
            properties.setProperty("profile", profile);
            properties.setProperty("targetLps", String.valueOf(targetLps));
            properties.setProperty("durationS", String.valueOf(durationS));
            properties.setProperty("compress", compress);
            properties.setProperty("sendThreadCount", String.valueOf(sendThreadCount));
            properties.setProperty("callbackFromSenderThread", String.valueOf(callbackFromSenderThread));
            properties.setProperty("persistent", String.valueOf(persistent));
            properties.setProperty("packetLogBytes", String.valueOf(packetLogBytes));
            properties.setProperty("packetLogCount", String.valueOf(packetLogCount));
            properties.setProperty("packetTimeoutMs", String.valueOf(packetTimeoutMs));
            properties.setProperty("maxBufferLimit", String.valueOf(maxBufferLimit));
            properties.setProperty("persistentMaxFileCount", String.valueOf(persistentMaxFileCount));
            properties.setProperty("persistentMaxFileSize", String.valueOf(persistentMaxFileSize));
            properties.setProperty("persistentMaxLogCount", String.valueOf(persistentMaxLogCount));
            properties.setProperty("persistentFilePath", persistentFilePath);
            properties.setProperty("attemptedLogs", String.valueOf(attemptedLogs));
            properties.setProperty("acceptedLogs", String.valueOf(acceptedLogs));
            properties.setProperty("rejectedLogs", String.valueOf(rejectedLogs));
            properties.setProperty("callbackSuccess", String.valueOf(callbackSuccess));
            properties.setProperty("callbackFailure", String.valueOf(callbackFailure));
            properties.setProperty("successfulLogs", String.valueOf(successfulLogs));
            properties.setProperty("remainingLogs", String.valueOf(remainingLogs));
            properties.setProperty("invalidSuccessRanges", String.valueOf(invalidSuccessRanges));
            properties.setProperty("callbackLogBytes", String.valueOf(callbackLogBytes));
            properties.setProperty("callbackCompressedBytes", String.valueOf(callbackCompressedBytes));
            properties.setProperty("enqueueWallMs", String.valueOf(enqueueWallMs));
            properties.setProperty("destroyWallMs", String.valueOf(destroyWallMs));
            properties.setProperty("wallTotalMs", String.valueOf(wallTotalMs));
            properties.setProperty("steadyWallMs", String.valueOf(steadyWallMs));
            properties.setProperty("steadyProcessCpuMs", String.valueOf(steadyProcessCpuMs));
            properties.setProperty("steadySamplerCpuMs", String.valueOf(steadySamplerCpuMs));
            properties.setProperty("steadyCpuMs", String.valueOf(steadyCpuMs));
            properties.setProperty("processCpuMs", String.valueOf(processCpuMs));
            properties.setProperty("samplerCpuMs", String.valueOf(samplerCpuMs));
            properties.setProperty("cpuMs", String.valueOf(cpuMs));
            properties.setProperty("availableProcessors", String.valueOf(availableProcessors));
            properties.setProperty("steadyCpuPct1", formatDouble(steadyCpuPct1));
            properties.setProperty("steadyCpuPctTotal", formatDouble(steadyCpuPctTotal));
            properties.setProperty("processCpuPct1", formatDouble(processCpuPct1));
            properties.setProperty("processCpuPctTotal", formatDouble(processCpuPctTotal));
            properties.setProperty("samplerCpuPct1", formatDouble(samplerCpuPct1));
            properties.setProperty("samplerCpuPctTotal", formatDouble(samplerCpuPctTotal));
            properties.setProperty("cpuPct1", formatDouble(cpuPct1));
            properties.setProperty("cpuPctTotal", formatDouble(cpuPctTotal));
            properties.setProperty("pssPeakKb", String.valueOf(pssPeakKb));
            properties.setProperty("nativePssPeakKb", String.valueOf(nativePssPeakKb));
            properties.setProperty("dalvikPssPeakKb", String.valueOf(dalvikPssPeakKb));
            properties.setProperty("rssPeakKb", String.valueOf(rssPeakKb));
            properties.setProperty("rssCurrentKb", String.valueOf(rssCurrentKb));
            properties.setProperty("threadsPeak", String.valueOf(threadsPeak));
            properties.setProperty("steadyPssPeakKb", String.valueOf(steadyPssPeakKb));
            properties.setProperty("steadyRssPeakKb", String.valueOf(steadyRssPeakKb));
            properties.setProperty("steadyThreadsPeak", String.valueOf(steadyThreadsPeak));
            properties.setProperty("javaHeapPeakKb", String.valueOf(javaHeapPeakKb));
            properties.setProperty("enqueueAvgUs", String.valueOf(enqueueAvgUs));
            properties.setProperty("enqueueMaxUs", String.valueOf(enqueueMaxUs));
            properties.setProperty("acceptedLps", formatDouble(acceptedLps));
            properties.setProperty("callbackRawKbPerSec", formatDouble(callbackRawKbPerSec));
            properties.setProperty("callbackCompressedKbPerSec", formatDouble(callbackCompressedKbPerSec));
            properties.setProperty("compressionRatio", formatDouble(compressionRatio));
            properties.setProperty("destroyCompleted", String.valueOf(destroyCompleted));
            properties.setProperty("deviceModel", deviceModel);
            properties.setProperty("deviceSdk", String.valueOf(deviceSdk));
            properties.setProperty("deviceAbi", deviceAbi);
            properties.setProperty("lastFailure", sanitize(lastFailure));
            return properties;
        }

        private static String deviceAbi() {
            if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                return Build.SUPPORTED_ABIS[0];
            }
            return Build.CPU_ABI;
        }

        private static String formatDouble(double value) {
            return String.format(java.util.Locale.US, "%.2f", value);
        }
    }
}
