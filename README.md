# Batch Incident Intelligence Platform

> A Spring Boot 3 event-driven platform that ingests batch-job execution events, detects anomalies via dual ML detectors, and produces LLM-generated incident summaries with Slack alerts.

[![CI](https://github.com/BibinFrancisK/intelligent-batch-incident-handler/actions/workflows/ci.yml/badge.svg)](https://github.com/BibinFrancisK/intelligent-batch-incident-handler/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-black)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## Why This Exists

Nightly batch workloads — payroll runs, actuarial extracts, claims ingestion — process millions of rows on a fixed schedule. When a job degrades silently, the on-call engineer at 3am faces a wall of log output with no immediate signal for root cause. Triage is manual, slow, and error-prone under pressure.

This platform closes that gap. It consumes structured execution events from Kafka, maintains rolling metrics per job type, and scores each completed run against a statistical baseline. When a job's duration or error rate drifts beyond its norm, the platform opens an incident, calls an LLM to produce a structured root-cause summary, and dispatches a Slack alert — all within the same event-processing pipeline, without blocking the consumer or coupling incident creation to LLM availability.

---

## Architecture

```mermaid
flowchart LR
    subgraph PROD["Simulated Batch Producers"]
        P1["Batch Job Simulator\n(Spring Boot CLI)"]
    end

    subgraph KAFKA["Apache Kafka (KRaft mode)"]
        T1[("batch.events.v1")]
        T2[("batch.events.v1.retry")]
        T3[("batch.events.v1.dlq")]
        T4[("incidents.v1")]
    end

    subgraph APP["incident-intelligence-service (Spring Boot 3)"]
        C1["BatchEventConsumer\n(virtual threads)"]
        M1["MetricsExtractor"]
        A1["AnomalyDetector\n(Isolation Forest + EWMA)"]
        L1["IncidentSummarizer\n(LangChain4j)"]
        N1["Notifier"]
    end

    subgraph STORE["State"]
        D1[("DynamoDB Local\nincidents, metrics_state")]
    end

    subgraph OBS["Observability"]
        PR["Prometheus"]
        GR["Grafana"]
    end

    subgraph LLM["LLM Providers (pluggable)"]
        G1["Gemini (free tier)"]
        AN["Anthropic (optional)"]
    end

    SL["Slack Webhook"]

    P1 -->|JSON events| T1
    T1 --> C1
    C1 -->|metrics| M1
    M1 --> A1
    A1 -->|anomaly?| L1
    L1 <-->|prompt/response| LLM
    L1 --> T4
    L1 --> D1
    N1 --> SL
    T4 --> N1

    C1 -.retry.-> T2
    T2 --> C1
    C1 -.poison.-> T3

    APP --> PR
    PR --> GR
```

![Architecture](docs/img/architecture.png)

See [`docs/architecture.md`](docs/architecture.md) for the full design deep-dive including sequence diagrams, DynamoDB table layouts, and dual metrics path explanation.

---

## What It Demonstrates

- **Event-driven pipeline** — Kafka with retry topics, DLQ routing, and an idempotent consumer using DynamoDB conditional `PutItem` (`attribute_not_exists`). At-least-once delivery with exactly-once effect.
- **Reliability primitives** — Resilience4j circuit breakers on both the LLM and Slack paths. LLM failure writes `summary=null` tagged `llm_unavailable`; the incident is persisted and Slack fires regardless. The consumer is never blocked by external API latency.
- **Dual anomaly detection** — EWMA z-score (zero-dependency baseline, warm from first event) and Isolation Forest (Smile library, multi-dimensional) behind a sealed `AnomalyDetector` interface. Active implementation swapped via `application.yml` with no code changes.
- **Modern Java** — Java 21 virtual threads on the Kafka consumer executor (IO-bound workload); sealed interfaces with exhaustive compiler-enforced pattern matching; immutable records throughout the domain layer.
- **AI integration with cost discipline** — LangChain4j → Gemini with a bounded-context prompt (5 numeric features, no raw log data). Circuit breaker caps retry storms. Incidents are stored even when the LLM is unavailable; no event is lost due to an external API outage.
- **Observability** — Micrometer counters and timers → Prometheus → Grafana 6-panel dashboard (throughput, p95 duration, error rate, anomaly scores, incidents by severity, LLM latency). OpenTelemetry `traceId`/`spanId` injected into MDC for log-correlated traces.
- **Infrastructure as Code** — Terraform (HCL) provisions EC2 t3.micro + DynamoDB for short-lived AWS demo deployments. `terraform destroy` is documented and enforced in the runbook.
- **Testing** — JUnit 5, Testcontainers (Kafka + DynamoDB Local), `@DynamicPropertySource` for port wiring. Zero LLM or Slack calls in CI — enforced via `NoopProvider` and `NoopNotifier` in `application-test.yml`.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.3, Virtual Threads (Project Loom) |
| Messaging | Apache Kafka (KRaft), retry topics, DLQ, idempotent producer |
| Persistence | DynamoDB Local (dev) / AWS DynamoDB (prod via Terraform) |
| Anomaly Detection | EWMA z-score (baseline) + Isolation Forest (Smile library) |
| AI / LLM | LangChain4j → Gemini 1.5 Flash (pluggable via sealed interface) |
| Resilience | Resilience4j circuit breakers on LLM and Slack paths |
| Observability | Micrometer, Prometheus, Grafana, OpenTelemetry (log-correlated traces) |
| Infrastructure as Code | Terraform (HCL), Docker Compose |
| Testing | JUnit 5, Testcontainers (Kafka + DynamoDB Local), Spring Boot Test |

---

## Quickstart

**Prerequisites:** Java 21, Docker

```bash
# 1. Clone
git clone https://github.com/bibinfrancis404/intelligent-batch-incident-handler.git
cd intelligent-batch-incident-handler

# 2. Start infra (Kafka, Prometheus, Grafana, DynamoDB Local)
docker compose -f docker/docker-compose.yml up -d

# 3. Set env vars — skip if using noop providers (application.yml defaults)
export GEMINI_API_KEY=<your-gemini-key>
export SLACK_WEBHOOK_URL=<your-slack-webhook>

# 4. Run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

| Service | URL |
|---|---|
| App health | `http://<host-name>:8080/actuator/health` |
| Prometheus metrics | `http://<host-name>:8080/actuator/prometheus` |
| Prometheus UI | `http://<host-name>:9090` |
| Grafana | `http://<host-name>:3000` (admin / admin) |
| DynamoDB Local | `http://<host-name>:8000` |

---

## Inject an Anomaly

With the app running, a single command publishes 10 warm-up events (to seed the EWMA baseline) followed by one anomalous run — all in the same JVM, same Kafka partition, guaranteed ordering:

```bash
./mvnw exec:java \
  -Dexec.mainClass="io.batchintel.simulator.BatchSimulatorRunner" \
  -Dspring.profiles.active=local \
  -Dspring.main.web-application-type=none \
  "-Dexec.args=--jobType=ANNUITY_PAYOUT --anomaly=true"
```

The anomaly multiplies normal job duration by 5.2× and injects an 8% error rate. The consumer detects the z-score spike (> 3.5σ), calls Gemini for a root-cause summary, persists the incident to DynamoDB, and posts a structured Slack alert.

To simulate a different job type:

```bash
"-Dexec.args=--jobType=POLICY_RENEWAL --anomaly=true"
"-Dexec.args=--jobType=CLAIMS_INGEST --anomaly=true"
```

---

## Screenshots

**Grafana — 6-panel dashboard after anomaly injection**

![Grafana dashboard](docs/img/grafana.png)

**Slack — LLM-generated incident alert**

![Slack notification](docs/img/slack.png)

---

## Design Decisions

Key architectural choices and their rationale are documented in [`docs/decisions.md`](docs/decisions.md).

Notable choices:

- **Sealed interfaces** for `LlmProvider`, `AnomalyDetector`, and `Notifier` — exhaustive compiler-enforced pattern matching; new implementations require updating every `switch`, which is intentional
- **LLM out of the critical path** — anomaly persists to DynamoDB and Slack fires even when the Gemini circuit-breaker is open; `summary=null` is a valid incident state
- **EWMA z-score ships before Isolation Forest** — zero-dependency baseline, warm from the first event; Isolation Forest layers on top behind the same interface once enough training data accumulates
- **KRaft Kafka** (no Zookeeper) — eliminates ~300 MB of Docker memory and a separate container; production-grade since Kafka 3.3
- **DynamoDB over Postgres** — same API locally and on AWS via Terraform; conditional `PutItem` gives atomic idempotency without serializable transactions

---

## Running Tests

```bash
# All tests — unit + integration + E2E (Testcontainers)
./mvnw verify

# Single test class
./mvnw -Dtest=EwmaAnomalyDetectorTest test
```

Tests run with `incident.llm.provider=noop` and `incident.notify.provider=noop` (enforced in `src/test/resources/application-test.yml`). Zero Gemini or Slack calls are made during `./mvnw verify`.

---

## What I Would Do Next

- **Avro + Confluent Schema Registry** — enforce schema evolution at the broker; consumers fail fast on breaking changes rather than silently ignoring unknown fields
- **Anthropic provider** — wire `AnthropicProvider` behind the same `LlmProvider` sealed interface; one `@ConditionalOnProperty` change, zero business logic changes
- **DLQ replay CLI** — cursor-based replay of dead-letter events with selective filtering by `jobType` and time window
- **LLM response cache** — fingerprint-based cache keyed on `(jobType, severityBucket, featureVector hash)` to avoid redundant Gemini calls for recurring anomaly patterns
