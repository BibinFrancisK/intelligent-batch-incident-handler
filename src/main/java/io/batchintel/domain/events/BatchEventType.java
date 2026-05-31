package io.batchintel.domain.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JobStarted.class,   name = "JOB_STARTED"),
    @JsonSubTypes.Type(value = JobProgress.class,  name = "JOB_PROGRESS"),
    @JsonSubTypes.Type(value = JobCompleted.class, name = "JOB_COMPLETED"),
    @JsonSubTypes.Type(value = JobFailed.class,    name = "JOB_FAILED")
})
public sealed interface BatchEventType
    permits JobStarted, JobProgress, JobCompleted, JobFailed {}
