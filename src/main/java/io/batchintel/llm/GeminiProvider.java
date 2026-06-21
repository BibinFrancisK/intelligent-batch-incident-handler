package io.batchintel.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.batchintel.domain.incidents.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "incident.llm.provider", havingValue = "gemini")
public final class GeminiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private final GoogleAiGeminiChatModel chatModel;
    private final ObjectMapper objectMapper;

    public GeminiProvider(
            @Value("${langchain4j.google-ai-gemini.api-key}") String apiKey,
            @Value("${langchain4j.google-ai-gemini.model-name}") String modelName,
            @Value("${langchain4j.google-ai-gemini.timeout-seconds:15}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequestsAndResponses(false)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public IncidentSummary summarize(IncidentContext context) {
        var response = chatModel.generate(
                SystemMessage.from(PromptTemplates.SYSTEM),
                UserMessage.from(PromptTemplates.buildUserPrompt(context))
        );
        log.debug("Gemini response tokenUsage={}", response.tokenUsage());
        return parseResponse(response.content().text());
    }

    @Override
    public String name() {
        return "gemini";
    }

    private IncidentSummary parseResponse(String raw) {
        // strip any Markdown code fences the model may wrap the JSON in
        String json = raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").strip();
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> actions = new ArrayList<>();
            node.path("suggestedActions").forEach(n -> actions.add(n.asText()));
            return new IncidentSummary(
                    node.path("summary").asText(),
                    node.path("likelyCause").asText(),
                    Incident.Severity.valueOf(node.path("severity").asText("MEDIUM")),
                    actions
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini JSON response: " + raw, e);
        }
    }
}
