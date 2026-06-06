package io.batchintel.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchintel.domain.events.BatchEvent;
import io.batchintel.metrics.MetricsExtractor;
import io.batchintel.persistence.IdempotencyStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BatchEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final MetricsExtractor metricsExtractor;

    public BatchEventConsumer(ObjectMapper objectMapper,
                              IdempotencyStore idempotencyStore,
                              MetricsExtractor metricsExtractor) {
        this.objectMapper     = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.metricsExtractor = metricsExtractor;
    }

    @KafkaListener(topics = "${app.kafka.topics.batch-events}")
    public void consume(ConsumerRecord<String, String> record) {
        BatchEvent event;
        try {
            event = objectMapper.readValue(record.value(), BatchEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialization failure — routing to DLQ. raw={}", record.value(), e);
            throw new RuntimeException("Deserialization failure", e);
        }

        MDC.put("eventId", event.eventId());
        MDC.put("jobType", event.jobType().name());
        try {
            if (!idempotencyStore.isNew(event.eventId())) {
                log.info("Duplicate event skipped");
                return;
            }
            metricsExtractor.extract(event);
            log.info("Processing event type={} schemaVersion={}",
                event.payload().getClass().getSimpleName(), event.schemaVersion());
        } finally {
            MDC.clear();
        }
    }
}
