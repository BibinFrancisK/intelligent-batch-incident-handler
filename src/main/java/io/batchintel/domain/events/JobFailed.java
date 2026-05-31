package io.batchintel.domain.events;

public record JobFailed(
    String runId,
    String errorCode,
    String errorMessage,
    int retryCount
) implements BatchEventType {}
