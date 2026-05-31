package io.batchintel.simulator;

import io.batchintel.domain.events.*;
import io.batchintel.domain.metrics.JobType;

import java.time.Instant;
import java.util.*;

public class JobScenarioFactory {

    private static final Map<JobType, long[]> ROW_RANGES = Map.of(
        JobType.POLICY_RENEWAL, new long[]{80_000L,  120_000L},
        JobType.ANNUITY_PAYOUT, new long[]{140_000L, 160_000L},
        JobType.CLAIMS_INGEST,  new long[]{20_000L,   50_000L}
    );

    private static final Map<JobType, double[]> DURATION_RANGES = Map.of(
        JobType.POLICY_RENEWAL, new double[]{120.0, 200.0},
        JobType.ANNUITY_PAYOUT, new double[]{160.0, 220.0},
        JobType.CLAIMS_INGEST,  new double[]{ 40.0,  90.0}
    );

    private final Random random = new Random();

    public List<BatchEvent> buildNormal(JobType jobType) {
        String runId  = "run-" + System.currentTimeMillis();
        long   rows   = randomLong(ROW_RANGES.get(jobType));
        double dur    = randomDouble(DURATION_RANGES.get(jobType));
        String source = jobType.name().toLowerCase() + "_export.csv";

        return buildEvents(runId, jobType, rows, dur, 0, source);
    }

    public List<BatchEvent> buildAnomalous(JobType jobType) {
        String runId  = "run-anomaly-" + System.currentTimeMillis();
        long   rows   = randomLong(ROW_RANGES.get(jobType));
        double dur    = randomDouble(DURATION_RANGES.get(jobType)) * AnomalyInjector.DURATION_MULTIPLIER;
        int    errors = (int) (rows * AnomalyInjector.ERROR_RATE);
        String source = jobType.name().toLowerCase() + "_export.csv";

        return buildEvents(runId, jobType, rows, dur, errors, source);
    }

    private List<BatchEvent> buildEvents(String runId, JobType jobType,
                                         long rows, double durationSeconds,
                                         int errorCount, String source) {
        List<BatchEvent> events = new ArrayList<>();

        events.add(event(jobType, new JobStarted(runId, source, rows)));
        events.add(event(jobType, new JobProgress(runId, rows / 3, 33)));
        events.add(event(jobType, new JobProgress(runId, rows * 2 / 3, 66)));
        events.add(event(jobType, new JobCompleted(runId, rows, durationSeconds, errorCount, source)));

        return events;
    }

    private BatchEvent event(JobType jobType, BatchEventType payload) {
        return new BatchEvent(
            UUID.randomUUID().toString(),
            BatchEvent.CURRENT_SCHEMA_VERSION,
            jobType,
            Instant.now(),
            payload
        );
    }

    private long randomLong(long[] range) {
        return range[0] + (long) (random.nextDouble() * (range[1] - range[0]));
    }

    private double randomDouble(double[] range) {
        return range[0] + random.nextDouble() * (range[1] - range[0]);
    }
}
