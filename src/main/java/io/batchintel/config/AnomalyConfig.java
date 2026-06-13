package io.batchintel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "incident.anomaly")
public record AnomalyConfig(
        String              detector,
        double              threshold,
        EwmaConfig          ewma,
        IsolationForestConfig isolationForest
) {
    public record EwmaConfig(double alpha) {}

    public record IsolationForestConfig(int trees, int sampleSize) {}
}
