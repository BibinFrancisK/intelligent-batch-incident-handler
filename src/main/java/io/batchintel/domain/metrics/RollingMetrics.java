package io.batchintel.domain.metrics;

import java.time.Instant;

public record RollingMetrics(
        JobType jobType,
        long    count,
        double  sumDurationSeconds,
        long    sumErrorCount,
        long    sumRows,
        Instant updatedAt
) {
    public double meanDurationSeconds() {
        return count == 0 ? 0 : sumDurationSeconds / count;
    }

    public double errorRate() {
        return sumRows == 0 ? 0 : (double) sumErrorCount / sumRows;
    }
}
