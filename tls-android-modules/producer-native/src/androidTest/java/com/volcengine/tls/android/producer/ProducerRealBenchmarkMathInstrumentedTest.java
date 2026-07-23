package com.volcengine.tls.android.producer;

import junit.framework.TestCase;

public final class ProducerRealBenchmarkMathInstrumentedTest extends TestCase {

    public void testCpuMetrics_subtractsSamplerCpuBeforeReporting() {
        ProducerRealBenchmarkMath.CpuMetrics metrics = ProducerRealBenchmarkMath.cpuMetrics(1000, 250, 1000, 4);

        assertEquals(750L, metrics.producerCpuMs);
        assertEquals(75.0, metrics.cpuPctSingleCore);
        assertEquals(18.75, metrics.cpuPctTotal);
    }

    public void testStatusGate_requiresOkByDefault() {
        assertTrue(ProducerRealBenchmarkMath.isAcceptableStatus("OK", true));
        assertFalse(ProducerRealBenchmarkMath.isAcceptableStatus("DEGRADED", true));
        assertTrue(ProducerRealBenchmarkMath.isAcceptableStatus("DEGRADED", false));
    }
}
