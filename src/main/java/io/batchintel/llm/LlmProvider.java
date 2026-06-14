package io.batchintel.llm;

public sealed interface LlmProvider permits GeminiProvider, NoopProvider {
    IncidentSummary summarize(IncidentContext context);

    String name();
}
