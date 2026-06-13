package io.batchintel.ml;

import io.batchintel.persistence.MetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "incident.anomaly.detector", havingValue = "isolation-forest")
public class ModelBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelBootstrap.class);
    private static final int MIN_TRAINING_SAMPLES = 10;

    private final IsolationForestDetector detector;
    private final MetricsRepository metricsRepository;

    public ModelBootstrap(IsolationForestDetector detector, MetricsRepository metricsRepository) {
        this.detector = detector;
        this.metricsRepository = metricsRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        var rows = metricsRepository.findAll();

        if (rows.size() < MIN_TRAINING_SAMPLES) {
            log.warn("IsolationForest not fitted — only {} row(s) in metrics_state (min={}). EWMA fallback active.",
                rows.size(), MIN_TRAINING_SAMPLES);
            return;
        }

        double[][] matrix = rows.stream()
            .map(m -> new double[]{
                m.meanDurationSeconds(),
                m.errorRate(),
                m.count() == 0 ? 0.0 : (double) m.sumRows() / m.count(),
                0.0,  // hourOfDay — not available from aggregate metrics_state; padded
                0.0   // dayOfWeek — not available from aggregate metrics_state; padded
            })
            .toArray(double[][]::new);

        detector.fit(matrix);
        log.info("IsolationForest fitted on {} samples in {}ms", rows.size(), System.currentTimeMillis() - start);
    }
}
