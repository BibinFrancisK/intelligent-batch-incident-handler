package io.batchintel.domain.events;

public record JobCompleted(
    String runId,
    long rowsProcessed,
    double durationSeconds,
    int errorCount,
    String sourceFile
) implements BatchEventType {}
