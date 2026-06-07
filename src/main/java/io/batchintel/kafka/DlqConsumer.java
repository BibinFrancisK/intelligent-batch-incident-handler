package io.batchintel.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchintel.kafka.headers.RetryHeaders;
import io.batchintel.persistence.DlqRepository;
import io.batchintel.utils.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);
    private static final String DLT_EXCEPTION_HEADER = "kafka_dlt-exception-message";

    private final DlqRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DlqConsumer(DlqRepository dlqRepository, ObjectMapper objectMapper) {
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.batch-events-dlq}",
            groupId = Constants.KAFKA_CONSUMER_GROUP_ID + "-dlq")
    public void consume(ConsumerRecord<String, String> record) {
        int retryCount = RetryHeaders.getAttemptCount(record);
        String eventId = extractEventId(record);
        String error = extractError(record);

        MDC.put("eventId", eventId);
        try {
            log.error("DLQ message received retryCount={} topic={} partition={} offset={} error={}",
                    retryCount, record.topic(), record.partition(), record.offset(), error);
            dlqRepository.save(eventId, record.value(), error, retryCount);
        } finally {
            MDC.clear();
        }
    }

    private String extractEventId(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            JsonNode idNode = node.get("eventId");
            if (idNode != null && !idNode.isNull()) {
                return idNode.asText();
            }
        } catch (Exception e) {
            log.error("Payload is not valid JSON (poison message).");
        }
        return UUID.randomUUID().toString();
    }

    private String extractError(ConsumerRecord<String, String> record) {
        var header = record.headers().lastHeader(DLT_EXCEPTION_HEADER);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : "unknown";
    }
}
