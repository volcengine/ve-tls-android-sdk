package com.volcengine.tls.android.producer;

final class ProducerRealBenchmarkMath {

    private ProducerRealBenchmarkMath() {
    }

    static CpuMetrics cpuMetrics(long processCpuMs, long samplerCpuMs, long wallMs, int cores) {
        long safeWallMs = Math.max(1L, wallMs);
        int safeCores = Math.max(1, cores);
        long safeProcessCpuMs = Math.max(0L, processCpuMs);
        long safeSamplerCpuMs = Math.max(0L, samplerCpuMs);
        long producerCpuMs = Math.max(0L, safeProcessCpuMs - safeSamplerCpuMs);
        double processCpuPctSingleCore = (safeProcessCpuMs * 100.0) / safeWallMs;
        double samplerCpuPctSingleCore = (safeSamplerCpuMs * 100.0) / safeWallMs;
        double producerCpuPctSingleCore = (producerCpuMs * 100.0) / safeWallMs;
        return new CpuMetrics(
                safeProcessCpuMs,
                safeSamplerCpuMs,
                producerCpuMs,
                processCpuPctSingleCore,
                processCpuPctSingleCore / safeCores,
                samplerCpuPctSingleCore,
                samplerCpuPctSingleCore / safeCores,
                producerCpuPctSingleCore,
                producerCpuPctSingleCore / safeCores);
    }

    static String status(boolean destroyCompleted, long acceptedLogs, long callbackSuccess, long callbackFailure, long rejectedLogs) {
        if (!destroyCompleted || acceptedLogs <= 0 || (callbackSuccess + callbackFailure) <= 0) {
            return "FAIL";
        }
        if (rejectedLogs > 0 || callbackFailure > 0) {
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
        final long processCpuMs;
        final long samplerCpuMs;
        final long producerCpuMs;
        final double processCpuPctSingleCore;
        final double processCpuPctTotal;
        final double samplerCpuPctSingleCore;
        final double samplerCpuPctTotal;
        final double cpuPctSingleCore;
        final double cpuPctTotal;

        CpuMetrics(
                long processCpuMs,
                long samplerCpuMs,
                long producerCpuMs,
                double processCpuPctSingleCore,
                double processCpuPctTotal,
                double samplerCpuPctSingleCore,
                double samplerCpuPctTotal,
                double cpuPctSingleCore,
                double cpuPctTotal) {
            this.processCpuMs = processCpuMs;
            this.samplerCpuMs = samplerCpuMs;
            this.producerCpuMs = producerCpuMs;
            this.processCpuPctSingleCore = processCpuPctSingleCore;
            this.processCpuPctTotal = processCpuPctTotal;
            this.samplerCpuPctSingleCore = samplerCpuPctSingleCore;
            this.samplerCpuPctTotal = samplerCpuPctTotal;
            this.cpuPctSingleCore = cpuPctSingleCore;
            this.cpuPctTotal = cpuPctTotal;
        }
    }
}
