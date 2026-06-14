package io.batchintel.metrics;

import io.batchintel.domain.incidents.Incident;
import io.batchintel.domain.metrics.JobType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BatchMetrics {

    private final MeterRegistry registry;
    private final Map<JobType, Double> errorRates = new ConcurrentHashMap<>();  // Prometheus reads this map at scrape time.

    public BatchMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (JobType jt : JobType.values()) { // register one gauge per job type at startup
            errorRates.put(jt, 0.0);
            Gauge.builder("batch.job.error.rate", errorRates, er -> er.get(jt))
                .tag("jobType", jt.name())
                .register(registry);
        }
    }

    public void recordProcessed(JobType jt, String eventType) {
        registry.counter("batch.events.processed", "jobType", jt.name(), "eventType", eventType)
            .increment();
    }

    public void recordDuration(JobType jt, double seconds) {
        registry.timer("batch.job.duration", "jobType", jt.name())
            .record(Duration.ofMillis((long) (seconds * 1000)));
    }

    public void setErrorRate(JobType jt, double rate) {
        errorRates.put(jt, rate);
    }

    public void recordIncidentDetected(Incident.Severity severity) {
        registry.counter("incidents.detected", "severity", severity.name()).increment();
    }
}
