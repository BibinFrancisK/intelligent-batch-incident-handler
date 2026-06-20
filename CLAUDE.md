# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`incident-intelligence-service` is a Spring Boot 3 event-driven platform that ingests structured batch-job execution events from Kafka, extracts rolling operational metrics, detects anomalies using a dual-detector ML layer (EWMA z-score + Isolation Forest), and produces LLM-generated incident summaries via LangChain4j. Incidents are persisted to DynamoDB, surfaced in Grafana, and dispatched to Slack. The system architecture and key design decisions are documented in `docs/`.

## Build & run commands

```bash
# Boot all infra (Kafka KRaft, Prometheus, Grafana, DynamoDB Local)
docker compose -f docker/docker-compose.yml up -d

# Run the Spring Boot app (local profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run all tests (unit + integration + E2E via Testcontainers)
./mvnw verify

# Run a single test class
./mvnw -pl . -Dtest=EwmaAnomalyDetectorTest test

# Inject an anomaly for end-to-end testing
# -Dspring.profiles.active=local is required — exec:java is a plain JVM launch and does not
# honor -Dspring-boot.run.profiles; without it spring.kafka.bootstrap-servers is unresolved
./mvnw exec:java -Dexec.mainClass="io.batchintel.simulator.BatchSimulatorRunner" \
  -Dspring.profiles.active=local \
  -Dexec.args="--jobType=ANNUITY_PAYOUT --anomaly=true"

# Check DLQ depth
docker exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 --topic batch.events.v1.dlq

# Tail incidents topic
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic incidents.v1 --from-beginning

# Scan DynamoDB incidents table
aws dynamodb scan --table-name incidents --endpoint-url http://localhost:8000

# Terraform — provision AWS infra (EC2 + DynamoDB)
cd infra/terraform && terraform init && terraform apply -var="environment=demo"

# Terraform — destroy all AWS resources after testing
cd infra/terraform && terraform destroy -var="environment=demo"
```

## Architecture

Single Spring Boot 3 service with a simulator CLI bundled in the same JAR. No microservices split — the platform value lives in the event flow and observability layer, not in distribution.

**Event flow:**

```
BatchSimulatorRunner → Kafka (batch.events.v1)
  → BatchEventConsumer (virtual threads, idempotency via DDB conditional PutItem)
  → MetricsExtractor (rolling metrics per jobType → metrics_state in DDB)
  → AnomalyDetector (EWMA z-score or Isolation Forest, sealed interface)
  → IncidentSummarizer (LangChain4j → Gemini; circuit-breaker via Resilience4j)
  → SlackNotifier + DDB incidents table + incidents.v1 topic
```

**Retry / DLQ flow:** `batch.events.v1` → retry topic (3 attempts, exponential backoff, `x-retry-attempt` header) → `batch.events.v1.dlq` on poison/exhaustion.

**Key package layout** (`src/main/java/io/batchintel/`):

| Package | Responsibility |
|---|---|
| `domain/events/` | `BatchEvent` record + sealed `BatchEventType` hierarchy |
| `domain/metrics/` | `FeatureVector`, `RollingMetrics` records |
| `domain/incidents/` | `Incident`, `AnomalyScore`, `IncidentFingerprint` records |
| `kafka/` | Consumer, DLQ consumer, error handler config |
| `persistence/` | `IdempotencyStore` interface + DynamoDB impl; repositories |
| `ml/` | Sealed `AnomalyDetector` interface; `EwmaAnomalyDetector`; `IsolationForestDetector` |
| `llm/` | Sealed `LlmProvider` interface; `GeminiProvider`, `NoopProvider`, `IncidentSummarizer` |
| `notify/` | Sealed `Notifier` interface; `SlackNotifier`, `NoopNotifier` |
| `simulator/` | `BatchSimulatorRunner` (CommandLineRunner), `AnomalyInjector` |
| `config/` | Kafka, DynamoDB, virtual threads, Resilience4j, observability configs |

## Key design decisions

- **Sealed interfaces throughout** (`LlmProvider`, `AnomalyDetector`, `Notifier`, `BatchEventType`) — exhaustive pattern matching, swappable implementations selected at startup via `application.yml`.
- **Virtual threads** for the Kafka consumer executor — IO-bound workload; poll loop stays on platform thread (safe).
- **Dual anomaly detectors** — EWMA z-score is the always-available baseline (zero deps, warm on startup); Isolation Forest (Smile library) layers on top behind the sealed interface. Both are explainable, which matters for incident response.
- **LLM is out of the critical path** — anomaly persists to DDB and Slack fires even if the LLM circuit-breaker is open; incident written with `summary=null` tagged `llm_unavailable`.
- **DynamoDB Local in `-inMemory` mode** for local/CI; matches the Terraform stack in `infra/terraform/`. No Postgres to avoid schema migration complexity at this scope.
- **KRaft Kafka (no Zookeeper)** — reduces operational footprint and memory overhead.
- **Terraform deploys EC2 + DynamoDB for demo; always `terraform destroy` after testing** — EC2 t3.micro runs Docker Compose (Kafka + Prometheus + Grafana) + Spring Boot app; DynamoDB tables are real AWS (free tier). Lambda is not used — it is incompatible with a long-running Kafka consumer.

## Infra RAM budget (Docker Compose)

| Service | Cap |
|---|---|
| Kafka (KRaft) | 512 MB (`KAFKA_HEAP_OPTS=-Xmx512m`) |
| Prometheus | 256 MB |
| Grafana | 256 MB |
| DynamoDB Local | 256 MB |
| Spring Boot app | 512 MB (runs outside Compose) |

## Testing

- **Unit tests**: detectors, summarizer, fingerprint, schema fixture (`src/test/resources/fixtures/batch-event-v1-sample.json`)
- **Integration tests**: `@Testcontainers` with `KafkaContainer` + `GenericContainer` (DynamoDB Local); `@DynamicPropertySource` wires bootstrap servers and DDB endpoint
- **E2E**: `EndToEndFlowIT` — anomaly inject → incident in DDB → `incidents.detected` counter > 0
- **`NoopProvider`** used for all tests — zero LLM API calls in CI; `NoopNotifier` for Slack

## Observability

- **Metrics**: Micrometer → `http://localhost:8080/actuator/prometheus` → Prometheus (`:9090`) → Grafana (`:3000`, admin/admin)
- **Grafana dashboard**: provisioned automatically from `docker/grafana/dashboards/batch-health.json`; 6 panels: throughput, error rate, p95 duration, anomaly scores, incidents by severity, LLM latency
- **Traces**: OpenTelemetry SDK with `traceId`/`spanId` injected into MDC — visible in JSON logs; no Jaeger (RAM constraint on local dev)
- **Logs**: Logback JSON encoder; fields include `eventId`, `jobType`, `traceId`, `spanId`

## Environment variables

| Variable | Purpose |
|---|---|
| `GEMINI_API_KEY` | Gemini free-tier key; set `incident.llm.provider=noop` to disable LLM calls |
| `SLACK_WEBHOOK_URL` | Incoming webhook URL; set `incident.notify.provider=noop` to disable |

Active provider is controlled by `incident.llm.provider` and `incident.notify.provider` in `application.yml` (values: `gemini`/`anthropic`/`noop` and `slack`/`noop`).

## Scope

Out of scope by design: Kubernetes/Helm, real AWS deployment, frontend/React UI, multi-tenancy, OAuth, microservices split, Kafka Streams, deep learning models. See `docs/decisions.md` for rationale on each.
