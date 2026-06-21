package io.batchintel.utils;

public final class Constants {

    private Constants() {
    }

    // Simulator CLI argument keys
    public static final String ARG_JOB_TYPE = "--jobType";
    public static final String ARG_ANOMALY = "--anomaly=true";
    public static final String ARG_COUNT = "--count";
    public static final String DEFAULT_SIMULATOR_COUNT = "1";
    public static final int SIMULATOR_WARM_UP_RUNS = 10; // normal runs published before an anomalous one to seed EWMA state

    // BatchEvent type discriminator values — must match the "eventType" field in Kafka JSON payloads
    public static final String EVENT_TYPE_JOB_STARTED = "JOB_STARTED";
    public static final String EVENT_TYPE_JOB_PROGRESS = "JOB_PROGRESS";
    public static final String EVENT_TYPE_JOB_COMPLETED = "JOB_COMPLETED";
    public static final String EVENT_TYPE_JOB_FAILED = "JOB_FAILED";

    // Kafka consumer group
    public static final String KAFKA_CONSUMER_GROUP_ID = "incident-intelligence";

    // Kafka listener virtual-thread executor
    public static final String KAFKA_LISTENER_THREAD_PREFIX = "kafka-listener-";

    // DynamoDB table names
    public static final String TABLE_PROCESSED_EVENTS = "processed_events";
    public static final String TABLE_METRICS_STATE = "metrics_state";
    public static final String TABLE_INCIDENTS = "incidents";

    // processed_events attribute names
    public static final String ATTR_EVENT_ID = "eventId";
    public static final String ATTR_TTL = "ttl";

    // metrics_state attribute names
    public static final String ATTR_JOB_TYPE = "jobType";
    public static final String ATTR_COUNT = "count";
    public static final String ATTR_SUM_DURATION_SECONDS = "sumDurationSeconds";
    public static final String ATTR_SUM_ERROR_COUNT = "sumErrorCount";
    public static final String ATTR_SUM_ROWS = "sumRows";
    public static final String ATTR_UPDATED_AT = "updatedAt";

    // incidents attribute names
    public static final String ATTR_INCIDENT_ID         = "incidentId";
    public static final String ATTR_SCHEMA_VERSION      = "schemaVersion";
    public static final String ATTR_ANOMALY_SCORE       = "anomalyScore";
    public static final String ATTR_DETECTOR_NAME       = "detectorName";
    public static final String ATTR_FINGERPRINT         = "fingerprint";
    public static final String ATTR_SEVERITY            = "severity";
    public static final String ATTR_SUMMARY             = "summary";
    public static final String ATTR_LIKELY_CAUSE        = "likelyCause";
    public static final String ATTR_SUGGESTED_ACTIONS   = "suggestedActions";
    public static final String ATTR_LLM_PROVIDER        = "llmProvider";
    public static final String ATTR_LLM_UNAVAILABLE     = "llmUnavailable";
    public static final String ATTR_DETECTED_AT         = "detectedAt";

    // Kafka topic names
    public static final String TOPIC_BATCH_EVENTS = "batch.events.v1";
    public static final String TOPIC_BATCH_EVENTS_RETRY = "batch.events.v1.retry";
    public static final String TOPIC_BATCH_EVENTS_DLQ = "batch.events.v1.dlq";
    public static final String TOPIC_INCIDENTS = "incidents.v1";

    // Header key used to track retry attempts across topic hops
    public static final String HEADER_RETRY_ATTEMPT = "x-retry-attempt";

    // DynamoDB table — dead-letter storage
    public static final String TABLE_DLQ_EVENTS = "dlq_events";

    // dlq_events attribute names
    public static final String ATTR_DLQ_EVENT_ID = "eventId";
    public static final String ATTR_DLQ_RAW_PAYLOAD = "rawPayload";
    public static final String ATTR_DLQ_ERROR = "error";
    public static final String ATTR_DLQ_RETRY_COUNT = "retryCount";
    public static final String ATTR_DLQ_FAILED_AT = "failedAt";
}
