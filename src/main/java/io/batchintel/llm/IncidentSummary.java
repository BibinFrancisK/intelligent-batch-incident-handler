package io.batchintel.llm;

import io.batchintel.domain.incidents.Incident;

import java.util.List;

public record IncidentSummary(
    String summary,
    String likelyCause,
    Incident.Severity severity,
    List<String> suggestedActions
) {
}
