package com.volcengine.tls.producer.sample;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerificationReportTest {
    @Test
    public void successCriteria_requiresAllCallbacksAndNoFailures() {
        VerificationReport report = VerificationReport.fromExecution(
                "burst",
                4,
                4,
                0,
                false,
                null,
                1200L);

        assertTrue(report.isSuccess());
        assertEquals("PASS", report.getVerdict());
    }

    @Test
    public void successCriteria_marksTimeoutAsFailure() {
        VerificationReport report = VerificationReport.fromExecution(
                "send-once",
                1,
                0,
                0,
                true,
                null,
                5000L);

        assertEquals("FAIL", report.getVerdict());
        assertTrue(report.toJson().contains("\"timeout\":true"));
    }
}
