package io.batchintel.domain.metrics;

public record FeatureVector(
        JobType jobType,
        double  durationSeconds,
        double  rollingMeanDuration,
        double  errorRate,
        long    rowCount,
        int     hourOfDay,
        int     dayOfWeek
) {}
