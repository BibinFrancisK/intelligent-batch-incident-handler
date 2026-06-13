package io.batchintel.ml;

import io.batchintel.config.AnomalyConfig;
import io.batchintel.domain.metrics.FeatureVector;
import io.batchintel.domain.metrics.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class IsolationForestDetectorTest {

    // Threshold 0.6: normal points score ≈ 0.5; an extreme spike in 5-dimensional space
    // typically scores 0.65–0.72, giving enough margin above 0.6 to be reliable across random seeds.
    private static final AnomalyConfig CONFIG = new AnomalyConfig(
        "isolation-forest", 0.6,
        new AnomalyConfig.EwmaConfig(0.2),
        new AnomalyConfig.IsolationForestConfig(100, 256));

    // 300 rows of plausible normal data: duration 10±2s, low error rate, ~1000 rows processed
    private static double[][] normalTrainingData(int rows, Random rng) {
        double[][] data = new double[rows][5];
        for (int i = 0; i < rows; i++) {
            data[i][0] = 10.0 + rng.nextGaussian() * 2;   // durationSeconds
            data[i][1] = Math.max(0, rng.nextGaussian() * 0.005); // errorRate ≈ 0–1%
            data[i][2] = 1000 + rng.nextGaussian() * 100;  // rowCount
            data[i][3] = rng.nextInt(24);                    // hourOfDay
            data[i][4] = rng.nextInt(7) + 1;                // dayOfWeek
        }
        return data;
    }

    private static FeatureVector fv(double durationSeconds) {
        return new FeatureVector(JobType.ANNUITY_PAYOUT, durationSeconds, 10.0, 0.01, 1000L, 10, 2);
    }

    @RepeatedTest(3)
    @DisplayName("extreme duration spike scores above threshold after training")
    void detectsSpikeAfterTraining() {
        var detector = new IsolationForestDetector(CONFIG);
        // Fresh random seed per repetition — confirms detection is robust to forest randomness.
        // 500s spike is ~245 std-devs from the 10±2s training distribution; reliably isolated in few tree splits.
        detector.fit(normalTrainingData(300, new Random()));

        var result = detector.score(fv(500.0));

        assertThat(result.anomalous()).isTrue();
        assertThat(result.score()).isGreaterThan(CONFIG.threshold());
    }

    @Test
    @DisplayName("in-distribution observation is not anomalous after training")
    void normalObservationIsNotAnomaly() {
        var detector = new IsolationForestDetector(CONFIG);
        detector.fit(normalTrainingData(300, new Random(42)));

        var result = detector.score(fv(10.0));

        assertThat(result.anomalous()).isFalse();
    }

    @Test
    @DisplayName("scoring before fit delegates to EWMA and tags reason accordingly")
    void coldStartFallsBackToEwma() {
        var detector = new IsolationForestDetector(CONFIG);

        var result = detector.score(fv(10.0));

        assertThat(result.reason()).contains("ewma fallback");
        assertThat(result.detectorName()).isEqualTo("isolation-forest");
    }
}
