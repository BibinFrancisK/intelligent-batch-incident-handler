package io.batchintel.llm;

import io.batchintel.domain.incidents.AnomalyScore;
import io.batchintel.domain.incidents.Incident;
import io.batchintel.domain.metrics.FeatureVector;
import io.batchintel.domain.metrics.JobType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentSummarizerTest {

    private CircuitBreaker circuitBreaker;
    private IncidentSummarizer summarizer;

    private static final FeatureVector NORMAL_FV = new FeatureVector(
        JobType.ANNUITY_PAYOUT, 120.0, 100.0, 0.01, 5000L, 10, 2);

    private static final AnomalyScore EWMA_HIGH_SCORE = new AnomalyScore(7.5, true, "z-score=7.5", "ewma");
    private static final AnomalyScore EWMA_MEDIUM_SCORE = new AnomalyScore(5.0, true, "z-score=5.0", "ewma");
    private static final AnomalyScore EWMA_LOW_SCORE = new AnomalyScore(3.8, true, "z-score=3.8", "ewma");
    private static final AnomalyScore IF_HIGH_SCORE = new AnomalyScore(0.9, true, "if-score=0.9", "isolation-forest");
    private static final AnomalyScore IF_MEDIUM_SCORE = new AnomalyScore(0.7, true, "if-score=0.7", "isolation-forest");

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        summarizer = new IncidentSummarizer(new NoopProvider(), circuitBreaker);
    }

    @Test
    @DisplayName("EWMA z-score above 6.0 maps to HIGH severity")
    void ewmaHighSeverity() {
        Incident incident = summarizer.summarize(NORMAL_FV, EWMA_HIGH_SCORE);

        assertThat(incident.severity()).isEqualTo(Incident.Severity.HIGH);
    }

    @Test
    @DisplayName("EWMA z-score between 4.0 and 6.0 maps to MEDIUM severity")
    void ewmaMediumSeverity() {
        Incident incident = summarizer.summarize(NORMAL_FV, EWMA_MEDIUM_SCORE);

        assertThat(incident.severity()).isEqualTo(Incident.Severity.MEDIUM);
    }

    @Test
    @DisplayName("EWMA z-score below 4.0 maps to LOW severity")
    void ewmaLowSeverity() {
        Incident incident = summarizer.summarize(NORMAL_FV, EWMA_LOW_SCORE);

        assertThat(incident.severity()).isEqualTo(Incident.Severity.LOW);
    }

    @Test
    @DisplayName("Isolation Forest score above 0.8 maps to HIGH severity")
    void isolationForestHighSeverity() {
        Incident incident = summarizer.summarize(NORMAL_FV, IF_HIGH_SCORE);

        assertThat(incident.severity()).isEqualTo(Incident.Severity.HIGH);
    }

    @Test
    @DisplayName("Isolation Forest score between 0.6 and 0.8 maps to MEDIUM severity")
    void isolationForestMediumSeverity() {
        Incident incident = summarizer.summarize(NORMAL_FV, IF_MEDIUM_SCORE);

        assertThat(incident.severity()).isEqualTo(Incident.Severity.MEDIUM);
    }

    @Test
    @DisplayName("incident record is fully populated when LLM provider returns a summary")
    void incidentFullyPopulatedOnSuccess() {
        Incident incident = summarizer.summarize(NORMAL_FV, EWMA_HIGH_SCORE);

        assertThat(incident.incidentId()).isNotBlank();
        assertThat(incident.schemaVersion()).isEqualTo(Incident.CURRENT_SCHEMA_VERSION);
        assertThat(incident.jobType()).isEqualTo(JobType.ANNUITY_PAYOUT);
        assertThat(incident.anomalyScore()).isEqualTo(7.5);
        assertThat(incident.detectorName()).isEqualTo("ewma");
        assertThat(incident.fingerprint()).hasSize(64);
        assertThat(incident.summary()).isNotBlank();
        assertThat(incident.likelyCause()).isNotBlank();
        assertThat(incident.suggestedActions()).isNotEmpty();
        assertThat(incident.llmUnavailable()).isFalse();
        assertThat(incident.llmProvider()).isEqualTo("noop");
        assertThat(incident.detectedAt()).isNotNull();
    }

    @Test
    @DisplayName("incident persists with summary=null and llmUnavailable=true when circuit breaker is open")
    void incidentPersistedWhenCircuitBreakerOpen() {
        // OPEN state causes CallNotPermittedException before the provider is invoked
        CircuitBreaker openBreaker = CircuitBreaker.of("test-open", CircuitBreakerConfig.ofDefaults());
        openBreaker.transitionToOpenState();

        IncidentSummarizer summarizerWithOpenCB = new IncidentSummarizer(new NoopProvider(), openBreaker);
        Incident incident = summarizerWithOpenCB.summarize(NORMAL_FV, EWMA_HIGH_SCORE);

        assertThat(incident.llmUnavailable()).isTrue();
        assertThat(incident.summary()).isNull();
        assertThat(incident.likelyCause()).isNull();
        assertThat(incident.suggestedActions()).isEmpty();
        assertThat(incident.llmProvider()).isEqualTo("unavailable");
        // severity and structural fields must still be populated
        assertThat(incident.severity()).isEqualTo(Incident.Severity.HIGH);
        assertThat(incident.fingerprint()).hasSize(64);
        assertThat(incident.incidentId()).isNotBlank();
    }
}
