# LLM Integration Design

This document covers the LLM provider architecture, prompt strategy, circuit-breaker behavior, and incident persistence for `incident-intelligence-service`.

---

## Provider Architecture

The LLM layer is designed around a sealed interface so the active provider can be swapped at startup without touching business logic.

```
sealed interface LlmProvider permits GeminiProvider, NoopProvider
    IncidentSummary summarize(IncidentContext context)
    String name()
```

Active provider is selected by `incident.llm.provider` in `application.yml`:

| Value | Provider class | When to use |
|---|---|---|
| `noop` | `NoopProvider` | Local dev, all tests — zero API calls |
| `gemini` | `GeminiProvider` | Production / manual validation |

Both are `@ConditionalOnProperty` components — exactly one is registered as a Spring bean at startup.

---

## Input: IncidentContext

`IncidentContext` carries the fields the LLM prompt needs. It is built from `FeatureVector` and `AnomalyScore` inside `IncidentSummarizer`:

| Field | Source | Purpose in prompt |
|---|---|---|
| `jobType` | `FeatureVector.jobType()` | Job identification |
| `anomalyScore` | `AnomalyScore.score()` | Deviation magnitude |
| `detectorName` | `AnomalyScore.detectorName()` | Which algorithm flagged it |
| `durationSeconds` | `FeatureVector.durationSeconds()` | Actual observed duration |
| `rollingMeanDuration` | `FeatureVector.rollingMeanDuration()` | Expected baseline |
| `errorRate` | `FeatureVector.errorRate()` | Error fraction (0–1) |
| `rowCount` | `FeatureVector.rowCount()` | Rows processed |

---

## Prompt Strategy

`PromptTemplates` defines two prompt components.

### System prompt

Instructs the model to return a strict JSON schema and nothing else:

```
You are a batch-job reliability engineer. Analyze the provided metrics and return ONLY
valid JSON matching this schema exactly — no markdown, no explanation:
{"summary":"...","likelyCause":"...","severity":"LOW|MEDIUM|HIGH","suggestedActions":["..."]}
summary must be ≤200 characters. likelyCause must be ≤300 characters.
```

Constraints are stated twice (schema + field length limit) to reduce the chance of the model ignoring them. "No markdown, no explanation" prevents code-fence wrapping that would break JSON parsing.

### User prompt

Single-line key-value string formatted from `IncidentContext`:

```
Job: ANNUITY_PAYOUT | AnomalyScore: 7.4320 | Detector: ewma | Duration: 987.5s | Mean: 192.0s | ErrorRate: 0.0012 | Rows: 152000
```

`GeminiProvider.parseResponse()` strips markdown code fences defensively before JSON parsing, in case the model ignores the system prompt constraint.

---

## Severity Derivation

Severity is derived in `IncidentSummarizer.deriveSeverity()` — not from the LLM response — so it is always deterministic and explainable regardless of LLM availability.

| Detector | LOW | MEDIUM | HIGH |
|---|---|---|---|
| `ewma` (z-score, unbounded) | score < 4.0 | 4.0 ≤ score < 6.0 | score ≥ 6.0 |
| `isolation-forest` (0–1 scale) | score < 0.6 | 0.6 ≤ score < 0.8 | score ≥ 0.8 |

The severity thresholds map to operational response levels: LOW = monitor; MEDIUM = investigate within the hour; HIGH = page on-call.

---

## Circuit Breaker

`IncidentSummarizer` wraps the LLM call in the `llmProviderCircuitBreaker` Resilience4j circuit breaker.

```
incident.llm.provider=gemini
    → CircuitBreaker.decorateSupplier(cb, () -> provider.summarize(ctx)).get()
         → SUCCESS:  Incident persisted with full summary
         → EXCEPTION: log.warn + summary=null + llmUnavailable=true
    → Incident always persisted and Slack always fires
```

Circuit breaker config (from `application.yml`):

| Property | Value | Rationale |
|---|---|---|
| `slidingWindowSize` | 10 | Track the last 10 calls |
| `failureRateThreshold` | 50 % | Trip open when half of recent calls fail |
| `waitDurationInOpenState` | 30 s | Give the upstream LLM API time to recover |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Probe cautiously before closing |

**The LLM is out of the critical path.** A tripped circuit breaker does not prevent incident persistence: `incidentRepository.save()` and `notifier.notify()` are always called. The incident is written with `summary=null`, `llmUnavailable=true`, and `llmProvider="unavailable"`.

---

## NoopProvider

`NoopProvider` is the always-available fallback:

- Returns a static canned `IncidentSummary` referencing the job type, duration, and detector name
- Never makes any network calls
- Used in all unit tests, integration tests, E2E tests, and local dev
- Enforced by `incident.llm.provider=noop` in `application-test.yml` and `application-local.yml`

Zero LLM API calls are made during `./mvnw verify`.

---

## GeminiProvider

`GeminiProvider` uses LangChain4j `0.35.0` with `langchain4j-google-ai-gemini`:

- Model: configured via `${GEMINI_MODEL_NAME:gemini-flash-latest}`
- API key: `${GEMINI_API_KEY}` (required when `incident.llm.provider=gemini`)
- Timeout: 3 seconds (hard timeout on the `GoogleAiGeminiChatModel`)
- Logging: disabled (`logRequestsAndResponses: false`) — API key must not appear in logs

Activate with:
```bash
export GEMINI_API_KEY=<your-free-tier-key>
# in application-local.yml: incident.llm.provider: gemini
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Output: Incident Record

`IncidentSummarizer.summarize()` always returns a fully populated `Incident` record:

| Field | LLM available | LLM unavailable |
|---|---|---|
| `summary` | LLM-generated (≤200 chars) | `null` |
| `likelyCause` | LLM-generated (≤300 chars) | `null` |
| `suggestedActions` | LLM-generated list | empty list |
| `llmUnavailable` | `false` | `true` |
| `llmProvider` | `"gemini"` or `"noop"` | `"unavailable"` |
| `severity` | deterministic (z-score / IF thresholds) | same |
| `fingerprint` | SHA-256 of `jobType|severity|hourBucket` | same |

The incident is persisted to `incidents` DynamoDB table, published to `incidents.v1` Kafka topic, and dispatched to the active `Notifier` — regardless of LLM availability.
