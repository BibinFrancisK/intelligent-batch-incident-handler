package io.batchintel.simulator;

public class AnomalyInjector {

    /** Multiplier applied to normal duration to simulate a latency spike. */
    public static final double DURATION_MULTIPLIER = 5.2;

    /** Fraction of rows that fail during an anomalous run. */
    public static final double ERROR_RATE = 0.08;

    private AnomalyInjector() {}
}
