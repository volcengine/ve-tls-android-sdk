package com.volcengine.tls.android.producer;

import junit.framework.TestCase;

public final class ProducerRealBenchmarkDeliveryInstrumentedTest extends TestCase {

    public void testDuplicateRangesAreCountedOnce() {
        ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        delivery.recordSuccessRange(1L, 3L);
        delivery.recordSuccessRange(1L, 3L);
        delivery.recordSuccessRange(2L, 4L);

        ProducerRealBenchmarkDelivery.Summary summary = delivery.snapshot(4L);

        assertEquals(4L, summary.successfulLogs);
        assertEquals(0L, summary.remainingLogs);
    }

    public void testMissingRangeRemainsIncomplete() {
        ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        delivery.recordSuccessRange(1L, 2L);
        delivery.recordSuccessRange(4L, 5L);

        ProducerRealBenchmarkDelivery.Summary summary = delivery.snapshot(5L);

        assertEquals(4L, summary.successfulLogs);
        assertEquals(1L, summary.remainingLogs);
        assertFalse(summary.isComplete());
    }

    public void testOutOfOrderRangesAreMerged() {
        ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        delivery.recordSuccessRange(5L, 6L);
        delivery.recordSuccessRange(1L, 2L);
        delivery.recordSuccessRange(3L, 5L);

        ProducerRealBenchmarkDelivery.Summary summary = delivery.snapshot(6L);

        assertEquals(6L, summary.successfulLogs);
        assertEquals(0L, summary.remainingLogs);
        assertTrue(summary.isComplete());
    }

    public void testCompleteCoverageAcrossAdjacentRanges() {
        ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        delivery.recordSuccessRange(1L, 2L);
        delivery.recordSuccessRange(3L, 5L);

        ProducerRealBenchmarkDelivery.Summary summary = delivery.snapshot(5L);

        assertEquals(5L, summary.successfulLogs);
        assertEquals(0L, summary.remainingLogs);
    }

    public void testUnknownAndInvalidRangesDoNotCountAsSuccessEvidence() {
        ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        delivery.recordSuccessRange(0L, 0L);
        delivery.recordSuccessRange(5L, 4L);
        delivery.recordSuccessRange(1L, 6L);

        ProducerRealBenchmarkDelivery.Summary summary = delivery.snapshot(5L);

        assertEquals(0L, summary.successfulLogs);
        assertEquals(5L, summary.remainingLogs);
        assertEquals(3L, summary.invalidSuccessRanges);
        assertFalse(summary.isComplete());
    }

    public void testInvalidRangeKeepsCompleteValidCoverageDegraded() {
        ProducerRealBenchmarkDelivery delivery = new ProducerRealBenchmarkDelivery();
        delivery.recordSuccessRange(1L, 5L);
        delivery.recordSuccessRange(0L, 0L);

        ProducerRealBenchmarkDelivery.Summary summary = delivery.snapshot(5L);

        assertEquals(5L, summary.successfulLogs);
        assertEquals(0L, summary.remainingLogs);
        assertEquals(1L, summary.invalidSuccessRanges);
        assertFalse(summary.isComplete());
        assertEquals("DEGRADED", ProducerRealBenchmarkDelivery.statusAfterCoverage("OK", summary));
    }

    public void testExistingStatusErrorsRemainHigherPriorityThanCoverage() {
        ProducerRealBenchmarkDelivery complete = new ProducerRealBenchmarkDelivery();
        complete.recordSuccessRange(1L, 2L);
        ProducerRealBenchmarkDelivery.Summary completeSummary = complete.snapshot(2L);

        assertEquals("FAIL", ProducerRealBenchmarkDelivery.statusAfterCoverage("FAIL", completeSummary));
        assertEquals("DEGRADED", ProducerRealBenchmarkDelivery.statusAfterCoverage("DEGRADED", completeSummary));
        assertEquals("OK", ProducerRealBenchmarkDelivery.statusAfterCoverage("OK", completeSummary));

        ProducerRealBenchmarkDelivery incomplete = new ProducerRealBenchmarkDelivery();
        incomplete.recordSuccessRange(1L, 1L);
        assertEquals(
                "DEGRADED",
                ProducerRealBenchmarkDelivery.statusAfterCoverage("OK", incomplete.snapshot(2L)));
    }
}
