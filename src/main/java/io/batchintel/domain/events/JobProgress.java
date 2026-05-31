package io.batchintel.domain.events;

public record JobProgress(
    String runId,
    long rowsProcessedSoFar,
    int progressPercent
) implements BatchEventType {}
