package io.batchintel.notify;

import io.batchintel.domain.incidents.Incident;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "incident.notify.provider", havingValue = "slack")
public final class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);
    private static final int MAX_FINGERPRINT_CACHE = 500;

    private final String webhookUrl;
    private final CircuitBreaker circuitBreaker;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // Bounded insertion-order set; oldest fingerprints evicted when cache exceeds 500 entries.
    // Prevents re-alerting the same anomaly type within the same hour.
    private final Set<String> sentFingerprints = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(MAX_FINGERPRINT_CACHE + 1, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_FINGERPRINT_CACHE;
                }
            })
    );

    public SlackNotifier(@Value("${SLACK_WEBHOOK_URL}") String webhookUrl,
                          @Qualifier("slackNotifierCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.webhookUrl = webhookUrl;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void notify(Incident incident) {
        if (!sentFingerprints.add(incident.fingerprint())) {
            log.info("Slack deduped incidentId={} fingerprint={}", incident.incidentId(), incident.fingerprint());
            return;
        }
        CircuitBreaker.decorateRunnable(circuitBreaker, () -> postToSlack(incident)).run();
    }

    @Override
    public String name() {
        return "slack";
    }

    private void postToSlack(Incident incident) {
        String body = buildSlackPayload(incident);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Slack webhook returned HTTP " + response.statusCode());
            }
            log.info("Slack notified incidentId={} status={}", incident.incidentId(), response.statusCode());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Slack webhook failed for incidentId=" + incident.incidentId(), e);
        }
    }

    private String buildSlackPayload(Incident incident) {
        String summary = incident.summary() != null ? incident.summary() : "Anomaly detected (LLM unavailable)";
        String likelyCause = incident.likelyCause() != null ? incident.likelyCause() : "Unknown";
        String color = switch (incident.severity()) {
            case HIGH   -> "#D00000";
            case MEDIUM -> "#FF8800";
            case LOW    -> "#FFCC00";
        };
        return """
                {"text":"*[%s]* Anomaly in *%s* — score %.4f (detector: %s)","attachments":[{"color":"%s","fields":[{"title":"Summary","value":"%s","short":false},{"title":"Likely Cause","value":"%s","short":false},{"title":"Incident ID","value":"%s","short":true},{"title":"LLM Provider","value":"%s","short":true}]}]}
                """.strip().formatted(
                incident.severity(),
                escapeJson(incident.jobType().name()),
                incident.anomalyScore(),
                escapeJson(incident.detectorName()),
                color,
                escapeJson(summary),
                escapeJson(likelyCause),
                incident.incidentId(),
                escapeJson(incident.llmProvider())
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
