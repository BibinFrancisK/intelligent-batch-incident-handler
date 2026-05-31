package io.batchintel.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.batchintel.domain.events.BatchEvent;
import io.batchintel.domain.metrics.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.batchintel.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class BatchSimulatorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchSimulatorRunner.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final JobScenarioFactory scenarioFactory = new JobScenarioFactory();

    public BatchSimulatorRunner(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.kafka.topics.batch-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
        this.topic         = topic;
    }

    public static void main(String[] args) {
        SpringApplication.run(io.batchintel.IncidentIntelligenceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String jobTypeArg = arg(args, Constants.ARG_JOB_TYPE);
        if (jobTypeArg == null) return; // dormant during normal app startup — only activates when --jobType is present

        JobType jobType = JobType.valueOf(jobTypeArg.toUpperCase());
        boolean anomaly = hasFlag(args, Constants.ARG_ANOMALY);
        int count = Integer.parseInt(argOrDefault(args, Constants.ARG_COUNT, Constants.DEFAULT_SIMULATOR_COUNT));

        log.info("Simulator starting: jobType={} anomaly={} count={}", jobType, anomaly, count);

        for (int i = 0; i < count; i++) {
            List<BatchEvent> scenario = anomaly
                ? scenarioFactory.buildAnomalous(jobType)
                : scenarioFactory.buildNormal(jobType);

            for (BatchEvent event : scenario) {
                String payload = objectMapper.writeValueAsString(event);
                // jobType is the partition key — preserves per-stream ordering
                kafkaTemplate.send(topic, event.jobType().name(), payload);
                log.info("Published eventId={} type={}", event.eventId(),
                    event.payload().getClass().getSimpleName());
            }
        }

        log.info("Simulator finished: {} scenario(s) published", count);
    }

    private static String arg(String[] args, String name) {
        return Arrays.stream(args)
            .filter(a -> a.startsWith(name + "="))
            .map(a -> a.substring(name.length() + 1))
            .findFirst().orElse(null);
    }

    private static String argOrDefault(String[] args, String name, String defaultVal) {
        String v = arg(args, name);
        return v != null ? v : defaultVal;
    }

    private static boolean hasFlag(String[] args, String flag) {
        return Arrays.asList(args).contains(flag);
    }
}
