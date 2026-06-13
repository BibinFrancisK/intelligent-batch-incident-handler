package io.batchintel.domain.incidents;

public record AnomalyScore(
        double  score,
        boolean anomalous,
        String  reason,
        String  detectorName
) {}
