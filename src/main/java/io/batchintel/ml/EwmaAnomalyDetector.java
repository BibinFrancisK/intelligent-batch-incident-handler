package io.batchintel.ml;

import io.batchintel.config.AnomalyConfig;
import io.batchintel.domain.incidents.AnomalyScore;
import io.batchintel.domain.metrics.FeatureVector;
import io.batchintel.domain.metrics.JobType;
import io.micrometer.observation.annotation.Observed;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Exponentially Weighted Moving Average.
 * If a batch program's execution time is way outside the normal speed, it is an anomaly.
 */
@Component
@ConditionalOnProperty(name = "incident.anomaly.detector", havingValue = "ewma", matchIfMissing = true)
public non-sealed class EwmaAnomalyDetector implements AnomalyDetector {

    private static final double EPSILON = 1e-9;
    // Suppress anomaly scoring until sigma is built from at least this many observations.
    // Without this guard, observation #2 always produces z >> threshold because sigma starts at 0.
    // At alpha=0.2, sigma stabilises to ~13s (for 60s normal range) after ~10 events; z_max < 3.5.
    private static final int MIN_WARM_UP_OBSERVATIONS = 10;

    private final double alpha;
    private final double threshold;
    private final ConcurrentHashMap<JobType, EwmaState> state = new ConcurrentHashMap<>();

    public EwmaAnomalyDetector(AnomalyConfig config) {
        this.alpha = config.ewma().alpha();
        this.threshold = config.threshold();
    }

    @Observed(name = "anomaly.score", contextualName = "ewma.score")
    @Override
    public AnomalyScore score(FeatureVector vector) {
        double x = vector.durationSeconds();
        EwmaState prev = state.get(vector.jobType());

        if (prev == null) {
            state.put(vector.jobType(), new EwmaState(x, 0.0, 1));
            return new AnomalyScore(0.0, false, "cold start — seeding EWMA state", name());
        }

        double mu = alpha * x + (1 - alpha) * prev.mu();
        double sigma = alpha * Math.abs(x - mu) + (1 - alpha) * prev.sigma();
        double z = (x - mu) / Math.max(sigma, EPSILON);
        int observations = prev.observations() + 1;

        state.put(vector.jobType(), new EwmaState(mu, sigma, observations));

        if (observations < MIN_WARM_UP_OBSERVATIONS) {
            String reason = String.format("warm-up (%d/%d observations) — z=%.2f not scored yet",
                observations, MIN_WARM_UP_OBSERVATIONS, z);
            return new AnomalyScore(Math.abs(z), false, reason, name());
        }

        boolean anomalous = Math.abs(z) > threshold;
        String reason = String.format("duration z-score=%.2f (mu=%.2fs, sigma=%.2fs)", z, mu, sigma);

        return new AnomalyScore(Math.abs(z), anomalous, reason, name());
    }

    @Override
    public String name() {
        return "ewma";
    }

    private record EwmaState(double mu, double sigma, int observations) {
    }
}
