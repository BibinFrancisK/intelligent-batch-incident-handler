package io.batchintel.domain.events;

import io.batchintel.domain.metrics.JobType;
import java.time.Instant;

public record BatchEvent(
    String eventId,
    String schemaVersion,
    JobType jobType,
    Instant timestamp,
    BatchEventType payload
) {
    public static final String CURRENT_SCHEMA_VERSION = "v1";
}
