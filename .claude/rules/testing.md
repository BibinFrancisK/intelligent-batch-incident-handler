# Testing Rules

- Source of truth: `CLAUDE.md`.

---

## Test Commands

| Command | Scope | When to Run |
|---------|-------|-------------|
| `./mvnw test` | Unit tests only | Always; fast feedback |
| `./mvnw verify` | Unit + integration + E2E (Testcontainers) | Before every PR |
| `./mvnw -Dtest=<ClassName> test` | Single test class | Debugging a specific failure |

---

## Test Pyramid

```
       /\
      /e2e\          EndToEndFlowIT — anomaly inject → incident in DDB
     /------\
    /   IT   \       BatchEventConsumerIT, IdempotencyStoreIT
   /----------\
  /   Unit     \    EwmaAnomalyDetectorTest, IsolationForestDetectorTest,
 /--------------\   IncidentSummarizerTest, IncidentFingerprintTest
```

---

## Coverage Targets by Layer

| Layer | Type | Target |
|-------|------|--------|
| `EwmaAnomalyDetector` | Unit | 90%+ — pure algorithm, deterministic |
| `IsolationForestDetector` | Unit | 80%+ — synthetic spike scenarios |
| `IncidentFingerprint` | Unit | 90%+ — dedupe correctness is critical |
| `IncidentSummarizer` | Unit (Noop) | 70%+ — mock LLM, test prompt building |
| `BatchEventConsumer` | Integration | Happy path + poison message → DLQ |
| `IdempotencyStore` | Integration | Duplicate `eventId` is a no-op |
| `EndToEndFlowIT` | E2E | One full anomaly → incident → metric counter |

---

## Testcontainers Rules

- Use `KafkaContainer` (`confluentinc/cp-kafka:7.6.1`) for all Kafka integration tests.
- Use `GenericContainer` (`amazon/dynamodb-local:2.5.2`) with `-inMemory -sharedDb` for DynamoDB tests.
- Always wire dynamic ports via `@DynamicPropertySource` — never hardcode `localhost:9092`.
- Containers declared `static` — reused across all tests in the class (startup cost paid once).

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    r.add("aws.dynamodb.endpoint", () -> "http://localhost:" + ddb.getMappedPort(8000));
}
```

---

## What NOT to Test

- Spring Boot auto-configuration wiring — it works by design.
- Gemini / Anthropic API responses — not deterministic; always use `NoopProvider` in tests.
- Slack webhook delivery — always use `NoopNotifier` in tests.
- DynamoDB Local internal behavior — test your code, not Amazon's library.

---

## NoopProvider Rule

- All tests must run with `incident.llm.provider=noop` and `incident.notify.provider=noop`.
- This is enforced in `src/test/resources/application-test.yml` — do not override in individual test classes.
- Zero LLM API calls must be made during `./mvnw verify`.

---

## Test Naming Convention

- Test files: `<Subject>Test.java` (unit), `<Subject>IT.java` (integration/E2E).
- Method names: plain English describing behavior, not implementation.
- Use `@DisplayName` for non-obvious scenario descriptions.

```java
@Test
@DisplayName("score above threshold when duration spikes 5x rolling average")
void detectsLatencyAnomaly() { ... }
```

---

## Schema Contract Test

- `BatchEventDeserializationTest` must assert that `src/test/resources/fixtures/batch-event-v1-sample.json`
  deserializes without error after every schema change.
- This is the canary for breaking schema evolution — a failure here means a backward-incompatible change.
