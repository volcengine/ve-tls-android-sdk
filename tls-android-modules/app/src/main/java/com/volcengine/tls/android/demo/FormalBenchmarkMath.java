package com.volcengine.tls.android.demo;

final class FormalBenchmarkMath {
    private FormalBenchmarkMath() {
    }

    static CpuMetrics cpuMetrics(long processCpuMs, long samplerCpuMs, long wallMs, int cores) {
        long safeWallMs = Math.max(1L, wallMs);
        int safeCores = Math.max(1, cores);
        long safeProcessCpuMs = Math.max(0L, processCpuMs);
        long safeSamplerCpuMs = Math.max(0L, samplerCpuMs);
        long producerCpuMs = Math.max(0L, safeProcessCpuMs - safeSamplerCpuMs);
        double producerCpuPctSingleCore = (producerCpuMs * 100.0) / safeWallMs;
        return new CpuMetrics(
                producerCpuMs,
                producerCpuPctSingleCore,
                producerCpuPctSingleCore / safeCores);
    }

    static String status(boolean drainCompleted, long acceptedLogs, long callbackSuccess, long callbackFailure, long rejectedLogs) {
        if (acceptedLogs <= 0) {
            return "FAIL";
        }
        if (!drainCompleted || (callbackSuccess + callbackFailure) <= 0 || rejectedLogs > 0 || callbackFailure > 0) {
            return "DEGRADED";
        }
        return "OK";
    }

    static boolean isAcceptableStatus(String status, boolean failOnDegraded) {
        if ("OK".equals(status)) {
            return true;
        }
        return !failOnDegraded && "DEGRADED".equals(status);
    }

    static final class CpuMetrics {
        final long cpuMs;
        final double cpuPctSingleCore;
        final double cpuPctTotal;

        CpuMetrics(long cpuMs, double cpuPctSingleCore, double cpuPctTotal) {
            this.cpuMs = cpuMs;
            this.cpuPctSingleCore = cpuPctSingleCore;
            this.cpuPctTotal = cpuPctTotal;
        }
    }
}
