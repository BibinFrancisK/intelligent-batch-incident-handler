package io.batchintel.llm;

import io.batchintel.domain.incidents.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "incident.llm.provider", havingValue = "noop")
public final class NoopProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopProvider.class);

    @Override
    public IncidentSummary summarize(IncidentContext context) {
        log.info("NoopProvider: returning static summary for jobType={}", context.jobType());
        return new IncidentSummary(
                "Anomaly detected in " + context.jobType() + " — duration " + context.durationSeconds()
                        + "s vs mean " + context.rollingMeanDuration() + "s",
                "Likely cause: job duration spike detected by " + context.detectorName(),
                Incident.Severity.MEDIUM,
                List.of("Inspect job logs", "Check upstream data volumes")
        );
    }

    @Override
    public String name() {
        return "noop";
    }
}
