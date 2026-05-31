package io.batchintel.utils;

public final class Constants {

    private Constants() {}

    // Simulator CLI argument keys
    public static final String ARG_JOB_TYPE = "--jobType";
    public static final String ARG_ANOMALY  = "--anomaly=true";
    public static final String ARG_COUNT    = "--count";
    public static final String DEFAULT_SIMULATOR_COUNT = "1";

    // BatchEvent type discriminator values — must match the "eventType" field in Kafka JSON payloads
    public static final String EVENT_TYPE_JOB_STARTED   = "JOB_STARTED";
    public static final String EVENT_TYPE_JOB_PROGRESS  = "JOB_PROGRESS";
    public static final String EVENT_TYPE_JOB_COMPLETED = "JOB_COMPLETED";
    public static final String EVENT_TYPE_JOB_FAILED    = "JOB_FAILED";

    // Kafka listener virtual-thread executor
    public static final String KAFKA_LISTENER_THREAD_PREFIX = "kafka-listener-";
}
