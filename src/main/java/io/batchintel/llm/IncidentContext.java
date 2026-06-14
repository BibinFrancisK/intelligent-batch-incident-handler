package io.batchintel.llm;

import io.batchintel.domain.metrics.JobType;

public record IncidentContext(
    JobType jobType,
    double anomalyScore,
    String detectorName,
    double durationSeconds,
    double rollingMeanDuration,
    double errorRate,
    long rowCount
) {
}
