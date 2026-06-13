package io.batchintel.ml;

import io.batchintel.config.AnomalyConfig;
import io.batchintel.domain.metrics.FeatureVector;
import io.batchintel.domain.metrics.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EwmaAnomalyDetectorTest {

    private static final AnomalyConfig CONFIG = new AnomalyConfig(
        "ewma", 3.5,
        new AnomalyConfig.EwmaConfig(0.2),
        new AnomalyConfig.IsolationForestConfig(100, 256));

    // Only durationSeconds matters for EWMA; remaining fields are test-irrelevant padding
    private static FeatureVector fv(JobType jobType, double durationSeconds) {
        return new FeatureVector(jobType, durationSeconds, 0.0, 0.0, 1000L, 10, 2);
    }

    @Test
    @DisplayName("score above threshold when duration spikes 5x rolling average")
    void detectsLatencySpike() {
        var detector = new EwmaAnomalyDetector(CONFIG);
        var rng = new Random(42);

        for (int i = 0; i < 20; i++) {
            detector.score(fv(JobType.ANNUITY_PAYOUT, 10.0 + (rng.nextDouble() * 2 - 1)));
        }

        var result = detector.score(fv(JobType.ANNUITY_PAYOUT, 50.0));

        assertThat(result.anomalous()).isTrue();
        assertThat(result.score()).isGreaterThan(CONFIG.threshold());
    }

    @Test
    @DisplayName("normal variance within ±1s does not trigger anomaly once sigma has stabilised")
    void normalVarianceIsNotAnomaly() {
        var detector = new EwmaAnomalyDetector(CONFIG);
        var rng = new Random(42);

        // Run 100 observations; sigma stabilizes within ~10 iterations (half-life ≈ log(0.1)/log(0.8) ≈ 10).
        // Assert only on the last 70 where steady-state sigma ≈ 0.4s → max z ≈ 2.5 < threshold 3.5.
        for (int i = 0; i < 100; i++) {
            double d = 10.0 + (rng.nextDouble() * 2 - 1);
            var result = detector.score(fv(JobType.POLICY_RENEWAL, d));
            if (i >= 30) {
                assertThat(result.anomalous())
                    .as("expected no anomaly at iteration %d (duration=%.2fs)", i, d)
                    .isFalse();
            }
        }
    }

    @Test
    @DisplayName("first observation seeds state without throwing and is never anomalous")
    void coldStartDoesNotThrow() {
        var detector = new EwmaAnomalyDetector(CONFIG);

        var result = detector.score(fv(JobType.CLAIMS_INGEST, 10.0));

        assertThat(result.anomalous()).isFalse();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).contains("cold start");
    }

    @Test
    @DisplayName("each job type maintains independent EWMA state")
    void differentJobTypesHaveSeparateState() {
        var detector = new EwmaAnomalyDetector(CONFIG);
        var rng = new Random(42);

        // Warm up both job types to stable sigma
        for (int i = 0; i < 30; i++) {
            double d = 10.0 + (rng.nextDouble() * 2 - 1);
            detector.score(fv(JobType.ANNUITY_PAYOUT, d));
            detector.score(fv(JobType.CLAIMS_INGEST, d));
        }

        // Spike CLAIMS_INGEST; ANNUITY_PAYOUT gets a normal next observation
        var claimsResult = detector.score(fv(JobType.CLAIMS_INGEST, 50.0));
        var annuityResult = detector.score(fv(JobType.ANNUITY_PAYOUT, 10.2));

        assertThat(claimsResult.anomalous()).isTrue();
        assertThat(annuityResult.anomalous()).isFalse();
    }
}
