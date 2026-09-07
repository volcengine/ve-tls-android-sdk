package com.volcengine.tls.android.producer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Each run uses a fresh client and unique fresh WAL directory, so native IDs start at 1.
final class ProducerRealBenchmarkDelivery {
    private final List<Range> successfulRanges = new ArrayList<>();
    private long invalidSuccessRanges;

    synchronized void recordSuccessRange(long startId, long endId) {
        if (startId <= 0 || endId < startId) {
            invalidSuccessRanges++;
            return;
        }
        successfulRanges.add(new Range(startId, endId));
    }

    synchronized Summary snapshot(long acceptedLogs) {
        long safeAcceptedLogs = Math.max(0L, acceptedLogs);
        long invalidRanges = invalidSuccessRanges;
        List<Range> usableRanges = new ArrayList<>();
        for (Range range : successfulRanges) {
            if (range.endId > safeAcceptedLogs) {
                invalidRanges++;
            } else {
                usableRanges.add(range);
            }
        }
        Collections.sort(usableRanges, RANGE_ORDER);

        long successfulLogs = 0L;
        long currentStart = 0L;
        long currentEnd = 0L;
        for (Range range : usableRanges) {
            if (currentStart == 0L) {
                currentStart = range.startId;
                currentEnd = range.endId;
            } else if (range.startId <= currentEnd
                    || (currentEnd < Long.MAX_VALUE && range.startId == currentEnd + 1L)) {
                currentEnd = Math.max(currentEnd, range.endId);
            } else {
                successfulLogs += currentEnd - currentStart + 1L;
                currentStart = range.startId;
                currentEnd = range.endId;
            }
        }
        if (currentStart != 0L) {
            successfulLogs += currentEnd - currentStart + 1L;
        }
        return new Summary(
                safeAcceptedLogs,
                successfulLogs,
                safeAcceptedLogs - successfulLogs,
                invalidRanges);
    }

    static String statusAfterCoverage(String existingStatus, Summary summary) {
        if (!"OK".equals(existingStatus)) {
            return existingStatus;
        }
        return summary.isComplete() ? "OK" : "DEGRADED";
    }

    static final class Summary {
        final long acceptedLogs;
        final long successfulLogs;
        final long remainingLogs;
        final long invalidSuccessRanges;

        Summary(long acceptedLogs, long successfulLogs, long remainingLogs, long invalidSuccessRanges) {
            this.acceptedLogs = acceptedLogs;
            this.successfulLogs = successfulLogs;
            this.remainingLogs = remainingLogs;
            this.invalidSuccessRanges = invalidSuccessRanges;
        }

        boolean isComplete() {
            return acceptedLogs > 0L && remainingLogs == 0L && invalidSuccessRanges == 0L;
        }
    }

    private static final Comparator<Range> RANGE_ORDER = new Comparator<Range>() {
        @Override
        public int compare(Range left, Range right) {
            int byStart = Long.compare(left.startId, right.startId);
            return byStart != 0 ? byStart : Long.compare(left.endId, right.endId);
        }
    };

    private static final class Range {
        final long startId;
        final long endId;

        Range(long startId, long endId) {
            this.startId = startId;
            this.endId = endId;
        }
    }
}
