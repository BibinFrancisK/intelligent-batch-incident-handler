package io.batchintel.ml;

import io.batchintel.config.AnomalyConfig;
import io.batchintel.domain.incidents.AnomalyScore;
import io.batchintel.domain.metrics.FeatureVector;
import io.micrometer.observation.annotation.Observed;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import smile.anomaly.IsolationForest;

import java.util.Properties;

/**
 * Isolation forest considers multiple dimensions (duration, error rate, row count, hour of day etc.). Needs historical job data.
 * If a job is usually slow on weekends, it does not flag that as an anomaly.
 */

@Component
@ConditionalOnProperty(name = "incident.anomaly.detector", havingValue = "isolation-forest")
public non-sealed class IsolationForestDetector implements AnomalyDetector {

    private final int ntrees;
    private final double threshold;
    private final EwmaAnomalyDetector ewmaFallback;
    private volatile IsolationForest model;

    public IsolationForestDetector(AnomalyConfig config) {
        this.ntrees = config.isolationForest().trees();
        this.threshold = config.threshold();
        // Direct instantiation — not Spring-managed; used only when model is not yet fitted
        this.ewmaFallback = new EwmaAnomalyDetector(config);
    }

    public void fit(double[][] data) {
        Properties props = new Properties();
        props.setProperty("smile.isolation.forest.trees", String.valueOf(ntrees));
        this.model = IsolationForest.fit(data, props);
    }

    @Observed(name = "anomaly.score", contextualName = "isolation-forest.score")
    @Override
    public AnomalyScore score(FeatureVector vector) {
        if (model == null) {
            AnomalyScore ewmaScore = ewmaFallback.score(vector);
            return new AnomalyScore(
                ewmaScore.score(),
                ewmaScore.anomalous(),
                "isolation-forest not fitted — ewma fallback: " + ewmaScore.reason(),
                name());
        }

        double[] features = toFeatures(vector);
        double rawScore = model.score(features);
        boolean anomalous = rawScore > threshold;
        String reason = String.format("isolation-forest score=%.4f (threshold=%.2f)", rawScore, threshold);

        return new AnomalyScore(rawScore, anomalous, reason, name());
    }

    @Override
    public String name() {
        return "isolation-forest";
    }

    private double[] toFeatures(FeatureVector v) {
        return new double[]{v.durationSeconds(), v.errorRate(), v.rowCount(), v.hourOfDay(), v.dayOfWeek()};
    }
}
