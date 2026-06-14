package io.batchintel.domain.incidents;

import io.batchintel.domain.metrics.JobType;

import java.time.Instant;
import java.util.List;

public record Incident(
    String incidentId,
    String schemaVersion,
    JobType jobType,
    double anomalyScore,
    String detectorName,
    String fingerprint,
    Severity severity,
    String summary,
    String likelyCause,
    List<String> suggestedActions,
    boolean llmUnavailable,
    Instant detectedAt,
    String llmProvider
) {
    public static final String CURRENT_SCHEMA_VERSION = "v1";

    public enum Severity {LOW, MEDIUM, HIGH}
}
