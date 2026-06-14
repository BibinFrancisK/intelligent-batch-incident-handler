package io.batchintel.llm;

public final class PromptTemplates {

    public static final String SYSTEM = """
            You are a batch-job reliability engineer. Analyze the provided metrics and return ONLY \
            valid JSON matching this schema exactly — no markdown, no explanation:
            {"summary":"...","likelyCause":"...","severity":"LOW|MEDIUM|HIGH","suggestedActions":["..."]}
            summary must be ≤200 characters. likelyCause must be ≤300 characters.""";

    public static String buildUserPrompt(IncidentContext ctx) {
        return "Job: " + ctx.jobType()
             + " | AnomalyScore: " + String.format("%.4f", ctx.anomalyScore())
             + " | Duration: " + String.format("%.1f", ctx.durationSeconds()) + "s"
             + " (rolling mean " + String.format("%.1f", ctx.rollingMeanDuration()) + "s)"
             + " | ErrorRate: " + String.format("%.4f", ctx.errorRate())
             + " | Rows: " + ctx.rowCount()
             + " | Detector: " + ctx.detectorName();
    }

    private PromptTemplates() {}
}
