package io.batchintel.ml;

import io.batchintel.domain.incidents.AnomalyScore;
import io.batchintel.domain.metrics.FeatureVector;

public sealed interface AnomalyDetector permits EwmaAnomalyDetector, IsolationForestDetector {

    AnomalyScore score(FeatureVector vector);

    String name();
}
