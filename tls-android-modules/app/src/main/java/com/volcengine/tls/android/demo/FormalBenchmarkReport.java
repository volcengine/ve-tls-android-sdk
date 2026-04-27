package com.volcengine.tls.android.demo;

import java.util.Locale;
import java.util.Properties;

final class FormalBenchmarkReport {
    final String mode;
    final String profile;
    final int targetLps;
    final String status;
    final double acceptedLps;
    final long acceptedLogs;
    final long rejectedLogs;
    final long callbackSuccess;
    final long callbackFailure;
    final boolean drainCompleted;
    final long cpuMs;
    final long buildLogWallMs;
    final long addLogWallMs;
    final long javaFlattenWallMs;
    final long nativeAddLogWallMs;
    final long nativeUtfDupWallMs;
    final long nativeProducerAddLogWallMs;
    final long nativeKeyLensWallMs;
    final long nativePersistentPathWallMs;
    final long nativeQueuePathWallMs;
    final long nativeTlsBatchBuilderWallMs;
    final long nativeTlsBatchFlushWallMs;
    final long nativeTlsBatchMergeWallMs;
    final long nativePersistentBuilderWallMs;
    final long nativePersistentAppendWallMs;
    final long nativePersistentEnqueueWallMs;
    final long nativePersistentEnqueueWaitBufferWallMs;
    final long nativePersistentEnqueueBuilderInitWallMs;
    final long nativePersistentEnqueueIngressPushWallMs;
    final long nativePersistentEnqueueIngressWaitQueueWallMs;
    final long nativePersistentEnqueueIngressBookkeepingWallMs;
    final long nativePersistentEnqueueIngressNotifyWallMs;
    final long nativeTlsBatchPreambleWallMs;
    final long nativeTlsBatchBookkeepingWallMs;
    final long nativePersistentAppendEncodeWallMs;
    final long nativePersistentAppendStoreWallMs;
    final long nativeTlsBatchFlushShellWallMs;
    final long nativeTlsBatchMergeShellWallMs;
    final long nativePersistentAppendSizingWallMs;
    final long nativePersistentAppendCapacityWallMs;
    final long nativePersistentAppendPostStoreWallMs;
    final long nativePersistentAppendRetryShellWallMs;
    final long nativePersistentAppendPrecheckWallMs;
    final long nativePersistentAppendMetaWallMs;
    final long nativePersistentAppendRetryPersistentMutexWallMs;
    final long nativePersistentAppendRetrySleepWallMs;
    final long nativePersistentAppendRetryProducerMutexWallMs;
    final long nativePersistentAppendStoreRotateWallMs;
    final long nativePersistentAppendStoreWriteWallMs;
    final long nativePersistentAppendRetryProducerLockWallMs;
    final long nativePersistentAppendRetryProducerNotifyWallMs;
    final long sendCount;
    final long sendWallMs;
    final long signWallMs;
    final long httpRequestWallMs;
    final long managerTaskCount;
    final long managerBuildTaskWallMs;
    final long managerPrepareTaskWallMs;
    final long managerCompressWallMs;
    final long managerPushTaskWallMs;
    final long enqueueWallMs;
    final long destroyWallMs;
    final long steadyWallMs;
    final long steadyCpuMs;
    final double steadyCpuPctTotal;
    final long steadyPssPeakKb;
    final long steadyRssPeakKb;
    final long steadyThreadsPeak;
    final double cpuPctTotal;
    final long wallTotalMs;
    final long pssPeakKb;
    final long rssPeakKb;
    final long threadsPeak;
    final int availableProcessors;
    final double rawKbPerSec;
    final double compressedKbPerSec;
    final double compressionRatio;
    final String lastFailure;

    FormalBenchmarkReport(
            String mode,
            String profile,
            int targetLps,
            String status,
            double acceptedLps,
            long acceptedLogs,
            long rejectedLogs,
            long callbackSuccess,
            long callbackFailure,
            boolean drainCompleted,
            long cpuMs,
            long buildLogWallMs,
            long addLogWallMs,
            long javaFlattenWallMs,
            long nativeAddLogWallMs,
            long nativeUtfDupWallMs,
            long nativeProducerAddLogWallMs,
            long nativeKeyLensWallMs,
            long nativePersistentPathWallMs,
            long nativeQueuePathWallMs,
            long nativeTlsBatchBuilderWallMs,
            long nativeTlsBatchFlushWallMs,
            long nativeTlsBatchMergeWallMs,
            long nativePersistentBuilderWallMs,
            long nativePersistentAppendWallMs,
            long nativePersistentEnqueueWallMs,
            long nativePersistentEnqueueWaitBufferWallMs,
            long nativePersistentEnqueueBuilderInitWallMs,
            long nativePersistentEnqueueIngressPushWallMs,
            long nativePersistentEnqueueIngressWaitQueueWallMs,
            long nativePersistentEnqueueIngressBookkeepingWallMs,
            long nativePersistentEnqueueIngressNotifyWallMs,
            long nativeTlsBatchPreambleWallMs,
            long nativeTlsBatchBookkeepingWallMs,
            long nativePersistentAppendEncodeWallMs,
            long nativePersistentAppendStoreWallMs,
            long nativeTlsBatchFlushShellWallMs,
            long nativeTlsBatchMergeShellWallMs,
            long nativePersistentAppendSizingWallMs,
            long nativePersistentAppendCapacityWallMs,
            long nativePersistentAppendPostStoreWallMs,
            long nativePersistentAppendRetryShellWallMs,
            long nativePersistentAppendPrecheckWallMs,
            long nativePersistentAppendMetaWallMs,
            long nativePersistentAppendRetryPersistentMutexWallMs,
            long nativePersistentAppendRetrySleepWallMs,
            long nativePersistentAppendRetryProducerMutexWallMs,
            long nativePersistentAppendStoreRotateWallMs,
            long nativePersistentAppendStoreWriteWallMs,
            long nativePersistentAppendRetryProducerLockWallMs,
            long nativePersistentAppendRetryProducerNotifyWallMs,
            long sendCount,
            long sendWallMs,
            long signWallMs,
            long httpRequestWallMs,
            long managerTaskCount,
            long managerBuildTaskWallMs,
            long managerPrepareTaskWallMs,
            long managerCompressWallMs,
            long managerPushTaskWallMs,
            long enqueueWallMs,
            long destroyWallMs,
            long steadyWallMs,
            long steadyCpuMs,
            double steadyCpuPctTotal,
            long steadyPssPeakKb,
            long steadyRssPeakKb,
            long steadyThreadsPeak,
            double cpuPctTotal,
            long wallTotalMs,
            long pssPeakKb,
            long rssPeakKb,
            long threadsPeak,
            int availableProcessors,
            double rawKbPerSec,
            double compressedKbPerSec,
            String lastFailure) {
        this.mode = mode;
        this.profile = profile;
        this.targetLps = targetLps;
        this.status = status;
        this.acceptedLps = acceptedLps;
        this.acceptedLogs = acceptedLogs;
        this.rejectedLogs = rejectedLogs;
        this.callbackSuccess = callbackSuccess;
        this.callbackFailure = callbackFailure;
        this.drainCompleted = drainCompleted;
        this.cpuMs = cpuMs;
        this.buildLogWallMs = buildLogWallMs;
        this.addLogWallMs = addLogWallMs;
        this.javaFlattenWallMs = javaFlattenWallMs;
        this.nativeAddLogWallMs = nativeAddLogWallMs;
        this.nativeUtfDupWallMs = nativeUtfDupWallMs;
        this.nativeProducerAddLogWallMs = nativeProducerAddLogWallMs;
        this.nativeKeyLensWallMs = nativeKeyLensWallMs;
        this.nativePersistentPathWallMs = nativePersistentPathWallMs;
        this.nativeQueuePathWallMs = nativeQueuePathWallMs;
        this.nativeTlsBatchBuilderWallMs = nativeTlsBatchBuilderWallMs;
        this.nativeTlsBatchFlushWallMs = nativeTlsBatchFlushWallMs;
        this.nativeTlsBatchMergeWallMs = nativeTlsBatchMergeWallMs;
        this.nativePersistentBuilderWallMs = nativePersistentBuilderWallMs;
        this.nativePersistentAppendWallMs = nativePersistentAppendWallMs;
        this.nativePersistentEnqueueWallMs = nativePersistentEnqueueWallMs;
        this.nativePersistentEnqueueWaitBufferWallMs = nativePersistentEnqueueWaitBufferWallMs;
        this.nativePersistentEnqueueBuilderInitWallMs = nativePersistentEnqueueBuilderInitWallMs;
        this.nativePersistentEnqueueIngressPushWallMs = nativePersistentEnqueueIngressPushWallMs;
        this.nativePersistentEnqueueIngressWaitQueueWallMs = nativePersistentEnqueueIngressWaitQueueWallMs;
        this.nativePersistentEnqueueIngressBookkeepingWallMs = nativePersistentEnqueueIngressBookkeepingWallMs;
        this.nativePersistentEnqueueIngressNotifyWallMs = nativePersistentEnqueueIngressNotifyWallMs;
        this.nativeTlsBatchPreambleWallMs = nativeTlsBatchPreambleWallMs;
        this.nativeTlsBatchBookkeepingWallMs = nativeTlsBatchBookkeepingWallMs;
        this.nativePersistentAppendEncodeWallMs = nativePersistentAppendEncodeWallMs;
        this.nativePersistentAppendStoreWallMs = nativePersistentAppendStoreWallMs;
        this.nativeTlsBatchFlushShellWallMs = nativeTlsBatchFlushShellWallMs;
        this.nativeTlsBatchMergeShellWallMs = nativeTlsBatchMergeShellWallMs;
        this.nativePersistentAppendSizingWallMs = nativePersistentAppendSizingWallMs;
        this.nativePersistentAppendCapacityWallMs = nativePersistentAppendCapacityWallMs;
        this.nativePersistentAppendPostStoreWallMs = nativePersistentAppendPostStoreWallMs;
        this.nativePersistentAppendRetryShellWallMs = nativePersistentAppendRetryShellWallMs;
        this.nativePersistentAppendPrecheckWallMs = nativePersistentAppendPrecheckWallMs;
        this.nativePersistentAppendMetaWallMs = nativePersistentAppendMetaWallMs;
        this.nativePersistentAppendRetryPersistentMutexWallMs = nativePersistentAppendRetryPersistentMutexWallMs;
        this.nativePersistentAppendRetrySleepWallMs = nativePersistentAppendRetrySleepWallMs;
        this.nativePersistentAppendRetryProducerMutexWallMs = nativePersistentAppendRetryProducerMutexWallMs;
        this.nativePersistentAppendStoreRotateWallMs = nativePersistentAppendStoreRotateWallMs;
        this.nativePersistentAppendStoreWriteWallMs = nativePersistentAppendStoreWriteWallMs;
        this.nativePersistentAppendRetryProducerLockWallMs = nativePersistentAppendRetryProducerLockWallMs;
        this.nativePersistentAppendRetryProducerNotifyWallMs = nativePersistentAppendRetryProducerNotifyWallMs;
        this.sendCount = sendCount;
        this.sendWallMs = sendWallMs;
        this.signWallMs = signWallMs;
        this.httpRequestWallMs = httpRequestWallMs;
        this.managerTaskCount = managerTaskCount;
        this.managerBuildTaskWallMs = managerBuildTaskWallMs;
        this.managerPrepareTaskWallMs = managerPrepareTaskWallMs;
        this.managerCompressWallMs = managerCompressWallMs;
        this.managerPushTaskWallMs = managerPushTaskWallMs;
        this.enqueueWallMs = enqueueWallMs;
        this.destroyWallMs = destroyWallMs;
        this.steadyWallMs = steadyWallMs;
        this.steadyCpuMs = steadyCpuMs;
        this.steadyCpuPctTotal = steadyCpuPctTotal;
        this.steadyPssPeakKb = steadyPssPeakKb;
        this.steadyRssPeakKb = steadyRssPeakKb;
        this.steadyThreadsPeak = steadyThreadsPeak;
        this.cpuPctTotal = cpuPctTotal;
        this.wallTotalMs = wallTotalMs;
        this.pssPeakKb = pssPeakKb;
        this.rssPeakKb = rssPeakKb;
        this.threadsPeak = threadsPeak;
        this.availableProcessors = availableProcessors;
        this.rawKbPerSec = rawKbPerSec;
        this.compressedKbPerSec = compressedKbPerSec;
        this.compressionRatio = compressedKbPerSec > 0.0 ? rawKbPerSec / compressedKbPerSec : 0.0;
        this.lastFailure = lastFailure == null ? "" : lastFailure;
    }

    Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("mode", mode);
        properties.setProperty("profile", profile);
        properties.setProperty("targetLps", String.valueOf(targetLps));
        properties.setProperty("status", status);
        properties.setProperty("acceptedLps", formatDouble(acceptedLps));
        properties.setProperty("acceptedLogs", String.valueOf(acceptedLogs));
        properties.setProperty("rejectedLogs", String.valueOf(rejectedLogs));
        properties.setProperty("callbackSuccess", String.valueOf(callbackSuccess));
        properties.setProperty("callbackFailure", String.valueOf(callbackFailure));
        properties.setProperty("drainCompleted", String.valueOf(drainCompleted));
        properties.setProperty("cpuMs", String.valueOf(cpuMs));
        properties.setProperty("buildLogWallMs", String.valueOf(buildLogWallMs));
        properties.setProperty("addLogWallMs", String.valueOf(addLogWallMs));
        properties.setProperty("javaFlattenWallMs", String.valueOf(javaFlattenWallMs));
        properties.setProperty("nativeAddLogWallMs", String.valueOf(nativeAddLogWallMs));
        properties.setProperty("nativeUtfDupWallMs", String.valueOf(nativeUtfDupWallMs));
        properties.setProperty("nativeProducerAddLogWallMs", String.valueOf(nativeProducerAddLogWallMs));
        properties.setProperty("nativeKeyLensWallMs", String.valueOf(nativeKeyLensWallMs));
        properties.setProperty("nativePersistentPathWallMs", String.valueOf(nativePersistentPathWallMs));
        properties.setProperty("nativeQueuePathWallMs", String.valueOf(nativeQueuePathWallMs));
        properties.setProperty("nativeTlsBatchBuilderWallMs", String.valueOf(nativeTlsBatchBuilderWallMs));
        properties.setProperty("nativeTlsBatchFlushWallMs", String.valueOf(nativeTlsBatchFlushWallMs));
        properties.setProperty("nativeTlsBatchMergeWallMs", String.valueOf(nativeTlsBatchMergeWallMs));
        properties.setProperty("nativePersistentBuilderWallMs", String.valueOf(nativePersistentBuilderWallMs));
        properties.setProperty("nativePersistentAppendWallMs", String.valueOf(nativePersistentAppendWallMs));
        properties.setProperty("nativePersistentEnqueueWallMs", String.valueOf(nativePersistentEnqueueWallMs));
        properties.setProperty("nativePersistentEnqueueWaitBufferWallMs", String.valueOf(nativePersistentEnqueueWaitBufferWallMs));
        properties.setProperty("nativePersistentEnqueueBuilderInitWallMs", String.valueOf(nativePersistentEnqueueBuilderInitWallMs));
        properties.setProperty("nativePersistentEnqueueIngressPushWallMs", String.valueOf(nativePersistentEnqueueIngressPushWallMs));
        properties.setProperty("nativePersistentEnqueueIngressWaitQueueWallMs", String.valueOf(nativePersistentEnqueueIngressWaitQueueWallMs));
        properties.setProperty("nativePersistentEnqueueIngressBookkeepingWallMs", String.valueOf(nativePersistentEnqueueIngressBookkeepingWallMs));
        properties.setProperty("nativePersistentEnqueueIngressNotifyWallMs", String.valueOf(nativePersistentEnqueueIngressNotifyWallMs));
        properties.setProperty("nativeTlsBatchPreambleWallMs", String.valueOf(nativeTlsBatchPreambleWallMs));
        properties.setProperty("nativeTlsBatchBookkeepingWallMs", String.valueOf(nativeTlsBatchBookkeepingWallMs));
        properties.setProperty("nativePersistentAppendEncodeWallMs", String.valueOf(nativePersistentAppendEncodeWallMs));
        properties.setProperty("nativePersistentAppendStoreWallMs", String.valueOf(nativePersistentAppendStoreWallMs));
        properties.setProperty("nativeTlsBatchFlushShellWallMs", String.valueOf(nativeTlsBatchFlushShellWallMs));
        properties.setProperty("nativeTlsBatchMergeShellWallMs", String.valueOf(nativeTlsBatchMergeShellWallMs));
        properties.setProperty("nativePersistentAppendSizingWallMs", String.valueOf(nativePersistentAppendSizingWallMs));
        properties.setProperty("nativePersistentAppendCapacityWallMs", String.valueOf(nativePersistentAppendCapacityWallMs));
        properties.setProperty("nativePersistentAppendPostStoreWallMs", String.valueOf(nativePersistentAppendPostStoreWallMs));
        properties.setProperty("nativePersistentAppendRetryShellWallMs", String.valueOf(nativePersistentAppendRetryShellWallMs));
        properties.setProperty("nativePersistentAppendPrecheckWallMs", String.valueOf(nativePersistentAppendPrecheckWallMs));
        properties.setProperty("nativePersistentAppendMetaWallMs", String.valueOf(nativePersistentAppendMetaWallMs));
        properties.setProperty("nativePersistentAppendRetryPersistentMutexWallMs", String.valueOf(nativePersistentAppendRetryPersistentMutexWallMs));
        properties.setProperty("nativePersistentAppendRetrySleepWallMs", String.valueOf(nativePersistentAppendRetrySleepWallMs));
        properties.setProperty("nativePersistentAppendRetryProducerMutexWallMs", String.valueOf(nativePersistentAppendRetryProducerMutexWallMs));
        properties.setProperty("nativePersistentAppendStoreRotateWallMs", String.valueOf(nativePersistentAppendStoreRotateWallMs));
        properties.setProperty("nativePersistentAppendStoreWriteWallMs", String.valueOf(nativePersistentAppendStoreWriteWallMs));
        properties.setProperty("nativePersistentAppendRetryProducerLockWallMs", String.valueOf(nativePersistentAppendRetryProducerLockWallMs));
        properties.setProperty("nativePersistentAppendRetryProducerNotifyWallMs", String.valueOf(nativePersistentAppendRetryProducerNotifyWallMs));
        properties.setProperty("sendCount", String.valueOf(sendCount));
        properties.setProperty("sendWallMs", String.valueOf(sendWallMs));
        properties.setProperty("signWallMs", String.valueOf(signWallMs));
        properties.setProperty("httpRequestWallMs", String.valueOf(httpRequestWallMs));
        properties.setProperty("managerTaskCount", String.valueOf(managerTaskCount));
        properties.setProperty("managerBuildTaskWallMs", String.valueOf(managerBuildTaskWallMs));
        properties.setProperty("managerPrepareTaskWallMs", String.valueOf(managerPrepareTaskWallMs));
        properties.setProperty("managerCompressWallMs", String.valueOf(managerCompressWallMs));
        properties.setProperty("managerPushTaskWallMs", String.valueOf(managerPushTaskWallMs));
        properties.setProperty("enqueueWallMs", String.valueOf(enqueueWallMs));
        properties.setProperty("destroyWallMs", String.valueOf(destroyWallMs));
        properties.setProperty("steadyWallMs", String.valueOf(steadyWallMs));
        properties.setProperty("steadyCpuMs", String.valueOf(steadyCpuMs));
        properties.setProperty("steadyCpuPctTotal", formatDouble(steadyCpuPctTotal));
        properties.setProperty("steadyPssPeakKb", String.valueOf(steadyPssPeakKb));
        properties.setProperty("steadyRssPeakKb", String.valueOf(steadyRssPeakKb));
        properties.setProperty("steadyThreadsPeak", String.valueOf(steadyThreadsPeak));
        properties.setProperty("wallTotalMs", String.valueOf(wallTotalMs));
        properties.setProperty("availableProcessors", String.valueOf(availableProcessors));
        properties.setProperty("cpuPctTotal", formatDouble(cpuPctTotal));
        properties.setProperty("pssPeakKb", String.valueOf(pssPeakKb));
        properties.setProperty("rssPeakKb", String.valueOf(rssPeakKb));
        properties.setProperty("threadsPeak", String.valueOf(threadsPeak));
        properties.setProperty("rawKbPerSec", formatDouble(rawKbPerSec));
        properties.setProperty("compressedKbPerSec", formatDouble(compressedKbPerSec));
        properties.setProperty("compressionRatio", formatDouble(compressionRatio));
        properties.setProperty("lastFailure", lastFailure);
        return properties;
    }

    String summaryLine() {
        return mode + "/" + profile + "@" + targetLps + "lps"
                + " status=" + status
                + " accepted=" + formatDouble(acceptedLps)
                + " logs=" + acceptedLogs
                + " steady_cpu=" + formatDouble(steadyCpuPctTotal)
                + " steady_pss=" + steadyPssPeakKb
                + " steady_rss=" + steadyRssPeakKb;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
