package io.batchintel.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.batchintel.domain.events.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchEventDeserialisationTest {

    // Mirrors Spring Boot's ObjectMapper defaults: JavaTimeModule for Instant,
    // FAIL_ON_UNKNOWN_PROPERTIES=false to honour the schema evolution rule.
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void fixtureDeserializesWithoutError() throws Exception {
        var json = getClass().getResourceAsStream("/fixtures/batch-event-v1-sample.json");
        assertThat(json).isNotNull();

        List<BatchEvent> events = objectMapper.readValue(json, new TypeReference<>() {});

        assertThat(events).hasSize(4);
    }

    @Test
    void jobStartedDeserializes() throws Exception {
        var events = loadFixture();
        assertThat(events.get(0).payload()).isInstanceOf(JobStarted.class);
        assertThat(((JobStarted) events.get(0).payload()).runId()).isEqualTo("run-2024-0115-001");
    }

    @Test
    void jobProgressDeserializes() throws Exception {
        var events = loadFixture();
        assertThat(events.get(1).payload()).isInstanceOf(JobProgress.class);
        assertThat(((JobProgress) events.get(1).payload()).progressPercent()).isEqualTo(33);
    }

    @Test
    void jobCompletedDeserializes() throws Exception {
        var events = loadFixture();
        assertThat(events.get(2).payload()).isInstanceOf(JobCompleted.class);
        var completed = (JobCompleted) events.get(2).payload();
        assertThat(completed.rowsProcessed()).isEqualTo(150_000L);
        assertThat(completed.durationSeconds()).isEqualTo(182.5);
        assertThat(completed.errorCount()).isEqualTo(0);
    }

    @Test
    void jobFailedDeserializes() throws Exception {
        var events = loadFixture();
        assertThat(events.get(3).payload()).isInstanceOf(JobFailed.class);
        assertThat(((JobFailed) events.get(3).payload()).errorCode()).isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        String json = """
            {
              "eventId": "test-id",
              "schemaVersion": "v1",
              "jobType": "POLICY_RENEWAL",
              "timestamp": "2024-01-15T02:00:00Z",
              "unknownFutureField": "should be ignored",
              "payload": {
                "eventType": "JOB_COMPLETED",
                "runId": "run-test",
                "rowsProcessed": 1000,
                "durationSeconds": 60.0,
                "errorCount": 0,
                "sourceFile": "test.csv",
                "anotherUnknownField": true
              }
            }
            """;

        var event = objectMapper.readValue(json, BatchEvent.class);
        assertThat(event.eventId()).isEqualTo("test-id");
        assertThat(event.payload()).isInstanceOf(JobCompleted.class);
    }

    private List<BatchEvent> loadFixture() throws Exception {
        var json = getClass().getResourceAsStream("/fixtures/batch-event-v1-sample.json");
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
