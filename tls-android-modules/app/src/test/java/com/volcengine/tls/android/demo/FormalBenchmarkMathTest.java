package com.volcengine.tls.android.demo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FormalBenchmarkMathTest {

    @Test
    public void cpuMetrics_subtractsSamplerCpuBeforeReporting() {
        FormalBenchmarkMath.CpuMetrics metrics = FormalBenchmarkMath.cpuMetrics(1000, 250, 1000, 4);

        assertEquals(750, metrics.cpuMs);
        assertEquals(75.0, metrics.cpuPctSingleCore, 0.01);
        assertEquals(18.75, metrics.cpuPctTotal, 0.01);
    }

    @Test
    public void status_prefersThroughputAndFailureSignalsOverCallbackCountParity() {
        assertEquals("OK", FormalBenchmarkMath.status(true, 100, 4, 0, 0));
        assertEquals("DEGRADED", FormalBenchmarkMath.status(false, 100, 4, 0, 0));
        assertEquals("DEGRADED", FormalBenchmarkMath.status(true, 100, 4, 1, 0));
        assertEquals("DEGRADED", FormalBenchmarkMath.status(true, 100, 4, 0, 1));
        assertEquals("FAIL", FormalBenchmarkMath.status(true, 0, 0, 0, 0));
        assertTrue(FormalBenchmarkMath.isAcceptableStatus("OK", true));
        assertFalse(FormalBenchmarkMath.isAcceptableStatus("DEGRADED", true));
        assertTrue(FormalBenchmarkMath.isAcceptableStatus("DEGRADED", false));
    }
}
