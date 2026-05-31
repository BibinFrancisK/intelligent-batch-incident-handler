package io.batchintel.domain.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.batchintel.utils.Constants;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = JobStarted.class,   name = Constants.EVENT_TYPE_JOB_STARTED),
    @JsonSubTypes.Type(value = JobProgress.class,  name = Constants.EVENT_TYPE_JOB_PROGRESS),
    @JsonSubTypes.Type(value = JobCompleted.class, name = Constants.EVENT_TYPE_JOB_COMPLETED),
    @JsonSubTypes.Type(value = JobFailed.class,    name = Constants.EVENT_TYPE_JOB_FAILED)
})
public sealed interface BatchEventType
    permits JobStarted, JobProgress, JobCompleted, JobFailed {}
