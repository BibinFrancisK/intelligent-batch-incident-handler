# Domain and Event Design Rules

- Source of truth: `CLAUDE.md`.

---

## Kafka Topic Conventions

- Topic naming: `<domain>.<event>.v<version>[.retry|.dlq]`
  - Examples: `batch.events.v1`, `batch.events.v1.retry`, `batch.events.v1.dlq`, `incidents.v1`
- Partition key is always `jobType` — preserves per-stream ordering.
- Never publish directly to the DLQ — only the error handler routes there after exhausting retries.
- New topics must be added to `docker/kafka-init.sh` with explicit retention config.

---

## Domain Record Rules

- All domain objects are Java `record`s — immutable, no setters.
- Records live in `domain/events/`, `domain/metrics/`, or `domain/incidents/` based on what they represent.
- No business logic inside records — records are pure data carriers.
- Every record used as a Kafka message payload must have a `schemaVersion` field (e.g. `String schemaVersion = "v1"`).

---

## Sealed Interface Rules

- The four sealed interfaces (`LlmProvider`, `AnomalyDetector`, `Notifier`, `BatchEventType`) must have exhaustive `permits` clauses.
- Every `switch` or `instanceof` check on a sealed type must be exhaustive — no default fallback that silently ignores cases.
- Active implementation is selected at startup via `application.yml` — never hardcoded in business logic.
- `NoopProvider` / `NoopNotifier` must always be a permitted implementation — used in all tests to prevent external calls.

---

## Package Structure Rules

- New classes go into the package that matches their responsibility (see `CLAUDE.md` package table).
- `config/` holds only Spring `@Configuration` and `@Bean` definitions — no business logic.
- Cross-cutting concerns (e.g. MDC enrichment, retry headers) go into a dedicated sub-package, not spread across consumers and producers.
- No circular dependencies between packages — domain packages must not import from kafka, ml, llm, or notify.

---

## BatchEvent Schema Evolution

- Add new fields as `@JsonProperty(required = false)` with a default — never remove or rename existing fields.
- Increment the `v` suffix in the topic name (e.g. `batch.events.v2`) for breaking changes.
- The fixture at `src/test/resources/fixtures/batch-event-v1-sample.json` must always parse cleanly — treat a failure as a breaking change.
- `FAIL_ON_UNKNOWN_PROPERTIES` must remain `false` — consumers must tolerate producers adding fields.

---

## Error Handling Rules

- Transient failures (network, timeout) → publish to retry topic via `DefaultErrorHandler`.
- Poison messages (parse failure, schema violation) → publish directly to DLQ — do not retry.
- LLM and Slack failures → handled by Resilience4j circuit breaker; incident is still persisted with `summary=null`.
- Never swallow exceptions silently — always log with `traceId` and `eventId` in MDC before rethrowing or routing.
