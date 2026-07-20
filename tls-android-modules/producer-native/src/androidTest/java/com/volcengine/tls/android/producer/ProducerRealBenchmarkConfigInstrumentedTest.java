package com.volcengine.tls.android.producer;

import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.File;
import java.util.Properties;

public final class ProducerRealBenchmarkConfigInstrumentedTest extends TestCase {

    public void testFrom_usesRequestedSdkResourceDefaultsWhenUnset() {
        ProducerRealBenchmarkScenario scenario = ProducerRealBenchmarkScenario.from(baseProps(), new Bundle());

        assertEquals(1024 * 1024, scenario.packetLogBytes);
        assertEquals(1024, scenario.packetLogCount);
        assertEquals(3000, scenario.packetTimeoutMs);
        assertEquals(64 * 1024 * 1024, scenario.maxBufferLimit);
        assertEquals(1, scenario.sendThreadCount);
        assertEquals(0, scenario.retryMaxAttempts);
        assertEquals(90 * 1000, scenario.retryTotalTimeoutMs);
        assertEquals(500, scenario.retryInitialIntervalMs);
        assertEquals(10 * 1000, scenario.retryMaxIntervalMs);
        assertEquals(LogProducerConfig.CompressType.LZ4, scenario.compressType);
        assertEquals(20000L, scenario.destroyAwaitMs);
        assertTrue(scenario.failOnDegraded);
        assertFalse(scenario.persistent);
    }

    public void testFrom_allowsDegradedWhenExplicitlyRequested() {
        Bundle args = new Bundle();
        args.putString("benchmarkFailOnDegraded", "false");

        ProducerRealBenchmarkScenario scenario = ProducerRealBenchmarkScenario.from(baseProps(), args);

        assertFalse(scenario.failOnDegraded);
    }

    public void testBuildConfig_enablesPersistentWithRunScopedPathAndRequiredLimits() {
        Properties props = baseProps();
        props.setProperty("persistent", "true");
        props.setProperty("runId", "persistent-run");

        ProducerRealBenchmarkScenario scenario = ProducerRealBenchmarkScenario.from(props, new Bundle());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        LogProducerConfig config = scenario.buildConfig(props, context);
        File expectedPath = new File(new File(context.getFilesDir(), "benchmark-persistent"), "persistent-run");

        assertTrue(config.isPersistent());
        assertEquals(expectedPath.getAbsolutePath(), config.getPersistentFilePath());
        assertEquals(4, config.getPersistentMaxFileCount());
        assertEquals(1024 * 1024, config.getPersistentMaxFileSize());
        assertEquals(65536, config.getPersistentMaxLogCount());
        assertEquals(64 * 1024 * 1024, config.getMaxBufferLimit());
        assertEquals(1, config.getSendThreadCount());
        assertEquals(0, config.getRetryMaxAttempts());
        assertEquals(20000, config.getDestroyWaitMs());
    }

    public void testBuildConfig_usesDestroyAwaitAsNativeDrainBudget() {
        Properties props = baseProps();
        props.setProperty("destroyAwaitMs", "4321");

        ProducerRealBenchmarkScenario scenario = ProducerRealBenchmarkScenario.from(props, new Bundle());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        LogProducerConfig config = scenario.buildConfig(props, context);

        assertEquals(4321L, scenario.destroyAwaitMs);
        assertEquals(4321, config.getDestroyWaitMs());
    }

    private static Properties baseProps() {
        Properties props = new Properties();
        props.setProperty("endpoint", "https://tls-cn-beijing.volces.com");
        props.setProperty("region", "cn-beijing");
        props.setProperty("topicId", "topic-id");
        props.setProperty("accessKeyId", "access-key-id");
        props.setProperty("accessKeySecret", "access-key-secret");
        return props;
    }
}
