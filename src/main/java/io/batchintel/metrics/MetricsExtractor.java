package io.batchintel.metrics;

import io.batchintel.domain.events.BatchEvent;
import io.batchintel.domain.events.JobCompleted;
import io.batchintel.domain.events.JobFailed;
import io.batchintel.domain.events.JobProgress;
import io.batchintel.domain.events.JobStarted;
import io.batchintel.persistence.MetricsRepository;
import org.springframework.stereotype.Component;

@Component
public class MetricsExtractor {

    private final MetricsRepository repository;
    private final BatchMetrics batchMetrics;

    public MetricsExtractor(MetricsRepository repository, BatchMetrics batchMetrics) {
        this.repository = repository;
        this.batchMetrics = batchMetrics;
    }

    public void extract(BatchEvent event) {
        // record every event type against the counter regardless of whether it folds into metrics_state
        batchMetrics.recordProcessed(event.jobType(), event.payload().getClass().getSimpleName());

        switch (event.payload()) {
            case JobCompleted c -> {
                repository.accumulate(event.jobType(), c.durationSeconds(), c.errorCount(), c.rowsProcessed());
                batchMetrics.recordDuration(event.jobType(), c.durationSeconds());
                double rate = c.rowsProcessed() == 0 ? 0.0 : (double) c.errorCount() / c.rowsProcessed();
                batchMetrics.setErrorRate(event.jobType(), rate);
            }
            case JobFailed ignored -> {
                repository.accumulate(event.jobType(), 0.0, 1, 0);
                batchMetrics.setErrorRate(event.jobType(), 1.0);
            }
            case JobStarted ignored  -> { /* lifecycle marker — nothing to aggregate */ }
            case JobProgress ignored -> { /* interim marker — nothing to aggregate */ }
        }
    }
}
