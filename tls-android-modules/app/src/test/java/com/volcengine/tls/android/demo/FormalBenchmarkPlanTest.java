package com.volcengine.tls.android.demo;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FormalBenchmarkPlanTest {

    @Test
    public void defaultScenarios_coversExpectedMatrix() {
        List<FormalBenchmarkPlan.Scenario> scenarios = FormalBenchmarkPlan.defaultScenarios();

        assertEquals(20, scenarios.size());
        assertTrue(contains(scenarios, "memory", "tls200", 1));
        assertTrue(contains(scenarios, "memory", "tls700", 500));
        assertTrue(contains(scenarios, "persistent", "tls200", 200));
        assertTrue(contains(scenarios, "persistent", "tls700", 10));
    }

    @Test
    public void quickScenarios_coversExpectedMatrix() {
        List<FormalBenchmarkPlan.Scenario> scenarios = FormalBenchmarkPlan.quickScenarios();

        assertEquals(8, scenarios.size());
        assertTrue(contains(scenarios, "memory", "tls200", 200));
        assertTrue(contains(scenarios, "memory", "tls700", 500));
        assertTrue(contains(scenarios, "persistent", "tls200", 200));
        assertTrue(contains(scenarios, "persistent", "tls700", 500));
    }

    @Test
    public void summaryMarkdown_rendersTableWithScenarioRows() {
        FormalBenchmarkReport report = new FormalBenchmarkReport(
                "memory",
                "tls200",
                100,
                "OK",
                99.5,
                995,
                0,
                100,
                0,
                true,
                1234,
                321,
                765,
                210,
                432,
                111,
                333,
                21,
                0,
                312,
                101,
                102,
                103,
                104,
                105,
                106,
                107,
                108,
                109,
                110,
                111,
                112,
                113,
                114,
                115,
                116,
                117,
                118,
                119,
                120,
                121,
                122,
                123,
                124,
                125,
                126,
                127,
                128,
                129,
                130,
                131,
                6,
                222,
                111,
                99,
                12,
                13,
                14,
                15,
                16,
                4321,
                210,
                60000,
                700,
                1.17,
                19000,
                64000,
                11,
                1.25,
                5678,
                20480,
                65536,
                12,
                8,
                20.5,
                10.2,
                "");

        String markdown = FormalBenchmarkSummary.renderMarkdown("run-1", java.util.Collections.singletonList(report));

        assertTrue(markdown.contains("# Producer Native Formal Benchmark"));
        assertTrue(markdown.contains("| mode | profile | target_lps | accepted_lps | accepted_logs | rejected_logs | callback_ok | callback_fail | drain_completed | cpu_ms | build_log_wall_ms | add_log_wall_ms | java_flatten_wall_ms | native_add_log_wall_ms | native_utf_dup_wall_ms | native_producer_add_log_wall_ms | native_key_lens_wall_ms | native_persistent_path_wall_ms | native_queue_path_wall_ms | native_tls_batch_builder_wall_ms | native_tls_batch_flush_wall_ms | native_tls_batch_merge_wall_ms | native_persistent_builder_wall_ms | native_persistent_append_wall_ms | native_persistent_enqueue_wall_ms | native_persistent_enqueue_wait_buffer_wall_ms | native_persistent_enqueue_builder_init_wall_ms | native_persistent_enqueue_ingress_push_wall_ms | native_persistent_enqueue_ingress_wait_queue_wall_ms | native_persistent_enqueue_ingress_bookkeeping_wall_ms | native_persistent_enqueue_ingress_notify_wall_ms | native_tls_batch_preamble_wall_ms | native_tls_batch_bookkeeping_wall_ms | native_persistent_append_encode_wall_ms | native_persistent_append_store_wall_ms | native_tls_batch_flush_shell_wall_ms | native_tls_batch_merge_shell_wall_ms | native_persistent_append_sizing_wall_ms | native_persistent_append_capacity_wall_ms | native_persistent_append_post_store_wall_ms | native_persistent_append_retry_shell_wall_ms | native_persistent_append_precheck_wall_ms | native_persistent_append_meta_wall_ms | native_persistent_append_retry_persistent_mutex_wall_ms | native_persistent_append_retry_sleep_wall_ms | native_persistent_append_retry_producer_mutex_wall_ms | native_persistent_append_store_rotate_wall_ms | native_persistent_append_store_write_wall_ms | native_persistent_append_retry_producer_lock_wall_ms | native_persistent_append_retry_producer_notify_wall_ms | send_count | send_wall_ms | sign_wall_ms | http_request_wall_ms | enqueue_wall_ms | destroy_wall_ms | steady_wall_ms | steady_cpu_ms | steady_cpu_pct_total | steady_pss_peak_kb | steady_rss_peak_kb | steady_threads_peak | wall_total_ms | available_processors | cpu_pct_total | pss_peak_kb | rss_peak_kb | threads_peak | raw_kb_s | compressed_kb_s | compression_ratio | status |"));
        assertTrue(markdown.contains("| memory | tls200 | 100 | 99.50 | 995 | 0 | 100 | 0 | true | 1234 | 321 | 765 | 210 | 432 | 111 | 333 | 21 | 0 | 312 | 101 | 102 | 103 | 104 | 105 | 106 | 107 | 108 | 109 | 110 | 111 | 112 | 113 | 114 | 115 | 116 | 117 | 118 | 119 | 120 | 121 | 122 | 123 | 124 | 125 | 126 | 127 | 128 | 129 | 130 | 131 | 6 | 222 | 111 | 99 | 4321 | 210 | 60000 | 700 | 1.17 | 19000 | 64000 | 11 | 5678 | 8 | 1.25 | 20480 | 65536 | 12 | 20.50 | 10.20 | 2.01 | OK |"));
    }

    @Test
    public void properties_includeUnifiedBenchmarkFields() {
        FormalBenchmarkReport report = new FormalBenchmarkReport(
                "persistent",
                "tls700",
                500,
                "OK",
                500.1,
                5001,
                1,
                5,
                0,
                false,
                2345,
                456,
                654,
                123,
                456,
                222,
                111,
                77,
                888,
                0,
                201,
                202,
                203,
                204,
                205,
                206,
                207,
                208,
                209,
                210,
                211,
                212,
                213,
                214,
                215,
                216,
                217,
                218,
                219,
                220,
                221,
                222,
                223,
                224,
                225,
                226,
                227,
                228,
                229,
                230,
                231,
                7,
                333,
                222,
                111,
                17,
                18,
                19,
                20,
                21,
                8765,
                111,
                60000,
                1234,
                2.06,
                130000,
                180000,
                40,
                2.01,
                9876,
                135110,
                186832,
                41,
                8,
                353.24,
                5.04,
                "");

        java.util.Properties properties = report.toProperties();

        assertEquals("5001", properties.getProperty("acceptedLogs"));
        assertEquals("1", properties.getProperty("rejectedLogs"));
        assertEquals("2345", properties.getProperty("cpuMs"));
        assertEquals("456", properties.getProperty("buildLogWallMs"));
        assertEquals("654", properties.getProperty("addLogWallMs"));
        assertEquals("123", properties.getProperty("javaFlattenWallMs"));
        assertEquals("456", properties.getProperty("nativeAddLogWallMs"));
        assertEquals("222", properties.getProperty("nativeUtfDupWallMs"));
        assertEquals("111", properties.getProperty("nativeProducerAddLogWallMs"));
        assertEquals("77", properties.getProperty("nativeKeyLensWallMs"));
        assertEquals("888", properties.getProperty("nativePersistentPathWallMs"));
        assertEquals("0", properties.getProperty("nativeQueuePathWallMs"));
        assertEquals("201", properties.getProperty("nativeTlsBatchBuilderWallMs"));
        assertEquals("202", properties.getProperty("nativeTlsBatchFlushWallMs"));
        assertEquals("203", properties.getProperty("nativeTlsBatchMergeWallMs"));
        assertEquals("204", properties.getProperty("nativePersistentBuilderWallMs"));
        assertEquals("205", properties.getProperty("nativePersistentAppendWallMs"));
        assertEquals("206", properties.getProperty("nativePersistentEnqueueWallMs"));
        assertEquals("207", properties.getProperty("nativePersistentEnqueueWaitBufferWallMs"));
        assertEquals("208", properties.getProperty("nativePersistentEnqueueBuilderInitWallMs"));
        assertEquals("209", properties.getProperty("nativePersistentEnqueueIngressPushWallMs"));
        assertEquals("210", properties.getProperty("nativePersistentEnqueueIngressWaitQueueWallMs"));
        assertEquals("211", properties.getProperty("nativePersistentEnqueueIngressBookkeepingWallMs"));
        assertEquals("212", properties.getProperty("nativePersistentEnqueueIngressNotifyWallMs"));
        assertEquals("213", properties.getProperty("nativeTlsBatchPreambleWallMs"));
        assertEquals("214", properties.getProperty("nativeTlsBatchBookkeepingWallMs"));
        assertEquals("215", properties.getProperty("nativePersistentAppendEncodeWallMs"));
        assertEquals("216", properties.getProperty("nativePersistentAppendStoreWallMs"));
        assertEquals("217", properties.getProperty("nativeTlsBatchFlushShellWallMs"));
        assertEquals("218", properties.getProperty("nativeTlsBatchMergeShellWallMs"));
        assertEquals("219", properties.getProperty("nativePersistentAppendSizingWallMs"));
        assertEquals("220", properties.getProperty("nativePersistentAppendCapacityWallMs"));
        assertEquals("221", properties.getProperty("nativePersistentAppendPostStoreWallMs"));
        assertEquals("222", properties.getProperty("nativePersistentAppendRetryShellWallMs"));
        assertEquals("223", properties.getProperty("nativePersistentAppendPrecheckWallMs"));
        assertEquals("224", properties.getProperty("nativePersistentAppendMetaWallMs"));
        assertEquals("225", properties.getProperty("nativePersistentAppendRetryPersistentMutexWallMs"));
        assertEquals("226", properties.getProperty("nativePersistentAppendRetrySleepWallMs"));
        assertEquals("227", properties.getProperty("nativePersistentAppendRetryProducerMutexWallMs"));
        assertEquals("228", properties.getProperty("nativePersistentAppendStoreRotateWallMs"));
        assertEquals("229", properties.getProperty("nativePersistentAppendStoreWriteWallMs"));
        assertEquals("230", properties.getProperty("nativePersistentAppendRetryProducerLockWallMs"));
        assertEquals("231", properties.getProperty("nativePersistentAppendRetryProducerNotifyWallMs"));
        assertEquals("7", properties.getProperty("sendCount"));
        assertEquals("333", properties.getProperty("sendWallMs"));
        assertEquals("222", properties.getProperty("signWallMs"));
        assertEquals("111", properties.getProperty("httpRequestWallMs"));
        assertEquals("17", properties.getProperty("managerTaskCount"));
        assertEquals("18", properties.getProperty("managerBuildTaskWallMs"));
        assertEquals("19", properties.getProperty("managerPrepareTaskWallMs"));
        assertEquals("20", properties.getProperty("managerCompressWallMs"));
        assertEquals("21", properties.getProperty("managerPushTaskWallMs"));
        assertEquals("8765", properties.getProperty("enqueueWallMs"));
        assertEquals("111", properties.getProperty("destroyWallMs"));
        assertEquals("60000", properties.getProperty("steadyWallMs"));
        assertEquals("1234", properties.getProperty("steadyCpuMs"));
        assertEquals("2.06", properties.getProperty("steadyCpuPctTotal"));
        assertEquals("130000", properties.getProperty("steadyPssPeakKb"));
        assertEquals("180000", properties.getProperty("steadyRssPeakKb"));
        assertEquals("40", properties.getProperty("steadyThreadsPeak"));
        assertEquals("9876", properties.getProperty("wallTotalMs"));
        assertEquals("8", properties.getProperty("availableProcessors"));
        assertEquals("false", properties.getProperty("drainCompleted"));
    }

    private static boolean contains(List<FormalBenchmarkPlan.Scenario> scenarios, String mode, String profile, int targetLps) {
        for (FormalBenchmarkPlan.Scenario scenario : scenarios) {
            if (mode.equals(scenario.mode) && profile.equals(scenario.profile) && targetLps == scenario.targetLps) {
                return true;
            }
        }
        return false;
    }
}
