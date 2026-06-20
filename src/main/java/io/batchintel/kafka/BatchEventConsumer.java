package io.batchintel.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchintel.domain.events.BatchEvent;
import io.batchintel.domain.incidents.AnomalyScore;
import io.batchintel.domain.incidents.Incident;
import io.batchintel.domain.metrics.FeatureVector;
import io.batchintel.llm.IncidentSummarizer;
import io.batchintel.metrics.BatchMetrics;
import io.batchintel.metrics.MetricsExtractor;
import io.batchintel.ml.AnomalyDetector;
import io.batchintel.notify.Notifier;
import io.batchintel.persistence.IdempotencyStore;
import io.batchintel.persistence.IncidentRepository;
import io.batchintel.utils.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BatchEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;
    private final MetricsExtractor metricsExtractor;
    private final AnomalyDetector anomalyDetector;
    private final IncidentSummarizer incidentSummarizer;
    private final IncidentRepository incidentRepository;
    private final Notifier notifier;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final BatchMetrics batchMetrics;

    public BatchEventConsumer(ObjectMapper objectMapper,
                              IdempotencyStore idempotencyStore,
                              MetricsExtractor metricsExtractor,
                              AnomalyDetector anomalyDetector,
                              IncidentSummarizer incidentSummarizer,
                              IncidentRepository incidentRepository,
                              Notifier notifier,
                              KafkaTemplate<String, String> kafkaTemplate,
                              BatchMetrics batchMetrics) {
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
        this.metricsExtractor = metricsExtractor;
        this.anomalyDetector = anomalyDetector;
        this.incidentSummarizer = incidentSummarizer;
        this.incidentRepository = incidentRepository;
        this.notifier = notifier;
        this.kafkaTemplate = kafkaTemplate;
        this.batchMetrics = batchMetrics;
    }

    @KafkaListener(topics = "${app.kafka.topics.batch-events}")
    public void consume(ConsumerRecord<String, String> record) throws JsonProcessingException {
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

            Optional<FeatureVector> featureVector = metricsExtractor.extract(event);
            log.info("Processed event type={} schemaVersion={}",
                event.payload().getClass().getSimpleName(), event.schemaVersion());

            if (featureVector.isPresent()) {
                FeatureVector fv = featureVector.get();
                AnomalyScore anomalyScore = anomalyDetector.score(fv);
                batchMetrics.recordAnomalyScore(fv.jobType(), anomalyScore.score());
                log.debug("Anomaly score jobType={} score={} detector={}",
                    fv.jobType(), anomalyScore.score(), anomalyScore.detectorName());

                if (anomalyScore.anomalous()) {
                    log.warn("Anomaly detected jobType={} score={} reason={} detector={}",
                        fv.jobType(), anomalyScore.score(),
                        anomalyScore.reason(), anomalyScore.detectorName());

                    Incident incident = incidentSummarizer.summarize(fv, anomalyScore);
                    incidentRepository.save(incident);
                    notifier.notify(incident);

                    String payload = objectMapper.writeValueAsString(incident);
                    kafkaTemplate.send(Constants.TOPIC_INCIDENTS, incident.jobType().name(), payload);

                    batchMetrics.recordIncidentDetected(incident.severity());

                    log.info("Incident persisted incidentId={} severity={} llmProvider={} llmUnavailable={}",
                        incident.incidentId(), incident.severity(),
                        incident.llmProvider(), incident.llmUnavailable());
                }
            }
        } finally {
            MDC.clear();
        }
    }
}
