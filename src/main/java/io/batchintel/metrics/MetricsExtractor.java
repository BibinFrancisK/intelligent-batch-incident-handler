package io.batchintel.metrics;

import io.batchintel.domain.events.BatchEvent;
import io.batchintel.domain.events.JobCompleted;
import io.batchintel.domain.events.JobFailed;
import io.batchintel.domain.events.JobProgress;
import io.batchintel.domain.events.JobStarted;
import io.batchintel.domain.metrics.FeatureVector;
import io.batchintel.persistence.MetricsRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class MetricsExtractor {

    private final MetricsRepository repository;
    private final BatchMetrics batchMetrics;

    public MetricsExtractor(MetricsRepository repository, BatchMetrics batchMetrics) {
        this.repository = repository;
        this.batchMetrics = batchMetrics;
    }

    public Optional<FeatureVector> extract(BatchEvent event) {
        batchMetrics.recordProcessed(event.jobType(), event.payload().getClass().getSimpleName());

        return switch (event.payload()) {
            case JobCompleted c -> {
                repository.accumulate(event.jobType(), c.durationSeconds(), c.errorCount(), c.rowsProcessed());
                batchMetrics.recordDuration(event.jobType(), c.durationSeconds());
                double rate = c.rowsProcessed() == 0 ? 0.0 : (double) c.errorCount() / c.rowsProcessed();
                batchMetrics.setErrorRate(event.jobType(), rate);

                var rolling = repository.find(event.jobType()).orElseThrow();
                yield Optional.of(new FeatureVector(
                    event.jobType(),
                    c.durationSeconds(),
                    rolling.meanDurationSeconds(),
                    rolling.errorRate(),
                    c.rowsProcessed(),
                    event.timestamp().atZone(ZoneOffset.UTC).getHour(),
                    event.timestamp().atZone(ZoneOffset.UTC).getDayOfWeek().getValue()
                ));
            }
            case JobFailed ignored -> {
                repository.accumulate(event.jobType(), 0.0, 1, 0);
                batchMetrics.setErrorRate(event.jobType(), 1.0);
                yield Optional.empty();
            }
            case JobStarted ignored -> Optional.empty(); // lifecycle marker — nothing to aggregate
            case JobProgress ignored -> Optional.empty(); // interim marker — nothing to aggregate
        };
    }
}
