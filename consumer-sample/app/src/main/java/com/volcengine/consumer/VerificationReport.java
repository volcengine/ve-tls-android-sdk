package com.volcengine.consumer;

final class VerificationReport {
    private final String scenario;
    private final String verdict;
    private final int attempted;
    private final int callbackSuccess;
    private final int callbackFailure;
    private final boolean timeout;
    private final String syncFailure;
    private final String lastCallbackFailure;
    private final long durationMs;

    private VerificationReport(
            String scenario,
            String verdict,
            int attempted,
            int callbackSuccess,
            int callbackFailure,
            boolean timeout,
            String syncFailure,
            String lastCallbackFailure,
            long durationMs) {
        this.scenario = scenario;
        this.verdict = verdict;
        this.attempted = attempted;
        this.callbackSuccess = callbackSuccess;
        this.callbackFailure = callbackFailure;
        this.timeout = timeout;
        this.syncFailure = syncFailure;
        this.lastCallbackFailure = lastCallbackFailure;
        this.durationMs = durationMs;
    }

    static VerificationReport fromExecution(
            String scenario,
            int attempted,
            int callbackSuccess,
            int callbackFailure,
            boolean timeout,
            String failureMessage,
            long durationMs) {
        boolean success = !timeout && failureMessage == null && attempted > 0 && callbackFailure == 0 && callbackSuccess == attempted;
        return new VerificationReport(
                scenario,
                success ? "PASS" : "FAIL",
                attempted,
                callbackSuccess,
                callbackFailure,
                timeout,
                failureMessage,
                failureMessage,
                durationMs);
    }

    static VerificationReport syncFailure(String scenario, int attempted, String failureMessage, long durationMs) {
        return new VerificationReport(scenario, "FAIL", attempted, 0, 0, false, failureMessage, failureMessage, durationMs);
    }

    boolean isSuccess() {
        return "PASS".equals(verdict);
    }

    String getVerdict() {
        return verdict;
    }

    String toJson() {
        return "{" +
                "\"scenario\":" + quote(scenario) + "," +
                "\"verdict\":" + quote(verdict) + "," +
                "\"attempted\":" + attempted + "," +
                "\"callbackSuccess\":" + callbackSuccess + "," +
                "\"callbackFailure\":" + callbackFailure + "," +
                "\"timeout\":" + timeout + "," +
                "\"syncFailure\":" + quote(syncFailure) + "," +
                "\"lastCallbackFailure\":" + quote(lastCallbackFailure) + "," +
                "\"durationMs\":" + durationMs +
                "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
