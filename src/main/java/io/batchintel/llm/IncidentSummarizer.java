package io.batchintel.llm;

import io.batchintel.domain.incidents.Incident;
import io.batchintel.domain.incidents.AnomalyScore;
import io.batchintel.domain.incidents.IncidentFingerprint;
import io.batchintel.domain.metrics.FeatureVector;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class IncidentSummarizer {

    private static final Logger log = LoggerFactory.getLogger(IncidentSummarizer.class);

    private final LlmProvider provider;
    private final CircuitBreaker circuitBreaker;

    public IncidentSummarizer(LlmProvider provider,
                              @Qualifier("llmProviderCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.provider = provider;
        this.circuitBreaker = circuitBreaker;
    }

    @Observed(name = "incident.summarize", contextualName = "llm.summarize")
    public Incident summarize(FeatureVector fv, AnomalyScore score) {
        IncidentContext ctx = buildContext(fv, score);
        Incident.Severity severity = deriveSeverity(score);
        Instant now = Instant.now();
        String fingerprint = IncidentFingerprint.of(fv.jobType(), severity, now).value();

        IncidentSummary summary;
        boolean llmUnavailable;
        String llmProviderName;

        try {
            summary = CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> provider.summarize(ctx)).get();
            llmUnavailable = false;
            llmProviderName = provider.name();
        } catch (Exception e) {
            log.warn("LLM unavailable — persisting incident with summary=null. reason={}", e.getMessage());
            summary = null;
            llmUnavailable = true;
            llmProviderName = "unavailable";
        }

        return new Incident(
            UUID.randomUUID().toString(),
            Incident.CURRENT_SCHEMA_VERSION,
            fv.jobType(),
            score.score(),
            score.detectorName(),
            fingerprint,
            severity,
            summary != null ? summary.summary() : null,
            summary != null ? summary.likelyCause() : null,
            summary != null ? summary.suggestedActions() : List.of(),
            llmUnavailable,
            now,
            llmProviderName
        );
    }

    private IncidentContext buildContext(FeatureVector fv, AnomalyScore score) {
        return new IncidentContext(
            fv.jobType(),
            score.score(),
            score.detectorName(),
            fv.durationSeconds(),
            fv.rollingMeanDuration(),
            fv.errorRate(),
            fv.rowCount()
        );
    }

    private Incident.Severity deriveSeverity(AnomalyScore score) {
        double s = score.score();
        // EWMA produces z-scores (unbounded); Isolation Forest produces 0–1 anomaly scores
        if ("ewma".equals(score.detectorName())) {
            return s < 4.0 ? Incident.Severity.LOW
                : s < 6.0 ? Incident.Severity.MEDIUM
                  : Incident.Severity.HIGH;
        }
        return s < 0.6 ? Incident.Severity.LOW
            : s < 0.8 ? Incident.Severity.MEDIUM
              : Incident.Severity.HIGH;
    }
}
