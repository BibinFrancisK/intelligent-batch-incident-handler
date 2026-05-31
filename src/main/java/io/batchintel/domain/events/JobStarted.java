package io.batchintel.domain.events;

public record JobStarted(
    String runId,
    String sourceFile,
    long expectedRows
) implements BatchEventType {}
