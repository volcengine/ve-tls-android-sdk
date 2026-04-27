package com.volcengine.tls.android.demo;

import java.util.List;
import java.util.Locale;

final class FormalBenchmarkSummary {
    private FormalBenchmarkSummary() {
    }

    static String renderMarkdown(String runId, List<FormalBenchmarkReport> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Producer Native Formal Benchmark\n\n");
        sb.append("- runId: ").append(runId).append('\n');
        sb.append("- scenarios: ").append(reports.size()).append('\n');
        sb.append('\n');
        sb.append("| mode | profile | target_lps | accepted_lps | accepted_logs | rejected_logs | callback_ok | callback_fail | drain_completed | cpu_ms | build_log_wall_ms | add_log_wall_ms | java_flatten_wall_ms | native_add_log_wall_ms | native_utf_dup_wall_ms | native_producer_add_log_wall_ms | native_key_lens_wall_ms | native_persistent_path_wall_ms | native_queue_path_wall_ms | native_tls_batch_builder_wall_ms | native_tls_batch_flush_wall_ms | native_tls_batch_merge_wall_ms | native_persistent_builder_wall_ms | native_persistent_append_wall_ms | native_persistent_enqueue_wall_ms | native_persistent_enqueue_wait_buffer_wall_ms | native_persistent_enqueue_builder_init_wall_ms | native_persistent_enqueue_ingress_push_wall_ms | native_persistent_enqueue_ingress_wait_queue_wall_ms | native_persistent_enqueue_ingress_bookkeeping_wall_ms | native_persistent_enqueue_ingress_notify_wall_ms | native_tls_batch_preamble_wall_ms | native_tls_batch_bookkeeping_wall_ms | native_persistent_append_encode_wall_ms | native_persistent_append_store_wall_ms | native_tls_batch_flush_shell_wall_ms | native_tls_batch_merge_shell_wall_ms | native_persistent_append_sizing_wall_ms | native_persistent_append_capacity_wall_ms | native_persistent_append_post_store_wall_ms | native_persistent_append_retry_shell_wall_ms | native_persistent_append_precheck_wall_ms | native_persistent_append_meta_wall_ms | native_persistent_append_retry_persistent_mutex_wall_ms | native_persistent_append_retry_sleep_wall_ms | native_persistent_append_retry_producer_mutex_wall_ms | native_persistent_append_store_rotate_wall_ms | native_persistent_append_store_write_wall_ms | native_persistent_append_retry_producer_lock_wall_ms | native_persistent_append_retry_producer_notify_wall_ms | send_count | send_wall_ms | sign_wall_ms | http_request_wall_ms | enqueue_wall_ms | destroy_wall_ms | steady_wall_ms | steady_cpu_ms | steady_cpu_pct_total | steady_pss_peak_kb | steady_rss_peak_kb | steady_threads_peak | wall_total_ms | available_processors | cpu_pct_total | pss_peak_kb | rss_peak_kb | threads_peak | raw_kb_s | compressed_kb_s | compression_ratio | status |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (FormalBenchmarkReport report : reports) {
            sb.append("| ").append(report.mode)
                    .append(" | ").append(report.profile)
                    .append(" | ").append(report.targetLps)
                    .append(" | ").append(formatDouble(report.acceptedLps))
                    .append(" | ").append(report.acceptedLogs)
                    .append(" | ").append(report.rejectedLogs)
                    .append(" | ").append(report.callbackSuccess)
                    .append(" | ").append(report.callbackFailure)
                    .append(" | ").append(report.drainCompleted)
                    .append(" | ").append(report.cpuMs)
                    .append(" | ").append(report.buildLogWallMs)
                    .append(" | ").append(report.addLogWallMs)
                    .append(" | ").append(report.javaFlattenWallMs)
                    .append(" | ").append(report.nativeAddLogWallMs)
                    .append(" | ").append(report.nativeUtfDupWallMs)
                    .append(" | ").append(report.nativeProducerAddLogWallMs)
                    .append(" | ").append(report.nativeKeyLensWallMs)
                    .append(" | ").append(report.nativePersistentPathWallMs)
                    .append(" | ").append(report.nativeQueuePathWallMs)
                    .append(" | ").append(report.nativeTlsBatchBuilderWallMs)
                    .append(" | ").append(report.nativeTlsBatchFlushWallMs)
                    .append(" | ").append(report.nativeTlsBatchMergeWallMs)
                    .append(" | ").append(report.nativePersistentBuilderWallMs)
                    .append(" | ").append(report.nativePersistentAppendWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueWaitBufferWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueBuilderInitWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueIngressPushWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueIngressWaitQueueWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueIngressBookkeepingWallMs)
                    .append(" | ").append(report.nativePersistentEnqueueIngressNotifyWallMs)
                    .append(" | ").append(report.nativeTlsBatchPreambleWallMs)
                    .append(" | ").append(report.nativeTlsBatchBookkeepingWallMs)
                    .append(" | ").append(report.nativePersistentAppendEncodeWallMs)
                    .append(" | ").append(report.nativePersistentAppendStoreWallMs)
                    .append(" | ").append(report.nativeTlsBatchFlushShellWallMs)
                    .append(" | ").append(report.nativeTlsBatchMergeShellWallMs)
                    .append(" | ").append(report.nativePersistentAppendSizingWallMs)
                    .append(" | ").append(report.nativePersistentAppendCapacityWallMs)
                    .append(" | ").append(report.nativePersistentAppendPostStoreWallMs)
                    .append(" | ").append(report.nativePersistentAppendRetryShellWallMs)
                    .append(" | ").append(report.nativePersistentAppendPrecheckWallMs)
                    .append(" | ").append(report.nativePersistentAppendMetaWallMs)
                    .append(" | ").append(report.nativePersistentAppendRetryPersistentMutexWallMs)
                    .append(" | ").append(report.nativePersistentAppendRetrySleepWallMs)
                    .append(" | ").append(report.nativePersistentAppendRetryProducerMutexWallMs)
                    .append(" | ").append(report.nativePersistentAppendStoreRotateWallMs)
                    .append(" | ").append(report.nativePersistentAppendStoreWriteWallMs)
                    .append(" | ").append(report.nativePersistentAppendRetryProducerLockWallMs)
                    .append(" | ").append(report.nativePersistentAppendRetryProducerNotifyWallMs)
                    .append(" | ").append(report.sendCount)
                    .append(" | ").append(report.sendWallMs)
                    .append(" | ").append(report.signWallMs)
                    .append(" | ").append(report.httpRequestWallMs)
                    .append(" | ").append(report.enqueueWallMs)
                    .append(" | ").append(report.destroyWallMs)
                    .append(" | ").append(report.steadyWallMs)
                    .append(" | ").append(report.steadyCpuMs)
                    .append(" | ").append(formatDouble(report.steadyCpuPctTotal))
                    .append(" | ").append(report.steadyPssPeakKb)
                    .append(" | ").append(report.steadyRssPeakKb)
                    .append(" | ").append(report.steadyThreadsPeak)
                    .append(" | ").append(report.wallTotalMs)
                    .append(" | ").append(report.availableProcessors)
                    .append(" | ").append(formatDouble(report.cpuPctTotal))
                    .append(" | ").append(report.pssPeakKb)
                    .append(" | ").append(report.rssPeakKb)
                    .append(" | ").append(report.threadsPeak)
                    .append(" | ").append(formatDouble(report.rawKbPerSec))
                    .append(" | ").append(formatDouble(report.compressedKbPerSec))
                    .append(" | ").append(formatDouble(report.compressionRatio))
                    .append(" | ").append(report.status)
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
