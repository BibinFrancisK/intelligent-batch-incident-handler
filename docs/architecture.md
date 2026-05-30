# Architecture

## System Diagram

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
        L1["IncidentSummariser\n(LangChain4j)"]
        N1["NotificationDispatcher"]
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

---

## Data Flow (Happy Path)

```mermaid
sequenceDiagram
    autonumber
    participant Sim as Batch Simulator
    participant K as Kafka
    participant App as Spring Boot App
    participant ML as AnomalyDetector
    participant LLM as LangChain4j → Gemini
    participant DDB as DynamoDB
    participant Slack

    Sim->>K: Publish BatchEvent (JSON)
    K->>App: Poll batch (virtual thread per record)
    App->>App: Validate + dedupe by eventId (DDB conditional write)
    App->>App: Update rolling metrics
    App->>ML: score(featureVector)
    ML-->>App: anomalyScore > threshold
    App->>LLM: summarise(incidentContext)
    LLM-->>App: incidentSummary + likelyCause
    App->>DDB: PutItem(incident)
    App->>Slack: POST webhook
    App->>K: Publish incidents.v1
```

---

## Retry and DLQ Flow

```mermaid
flowchart LR
    A[batch.events.v1] --> C{Consume}
    C -->|success| OK[Commit offset]
    C -->|transient error| R1[Publish to retry\nheader: x-retry-attempt++]
    R1 --> C
    C -->|attempt > 3| DLQ[batch.events.v1.dlq]
    C -->|poison / parse error| DLQ
```

---

## Component Responsibilities

| Component | Responsibility |
|---|---|
| `BatchSimulatorRunner` | Emits structured `BatchEvent`s for 3 job types; supports `--anomaly=true` injection mode |
| `BatchEventConsumer` | At-least-once consumption; idempotency via DynamoDB conditional `PutItem`; routes failures to retry/DLQ |
| `MetricsExtractor` | Aggregates rolling metrics per `jobType` (row count, duration, error rate) into `metrics_state` |
| `AnomalyDetector` | Sealed interface — `EwmaAnomalyDetector` (baseline) or `IsolationForestDetector` (advanced), selected via config |
| `IncidentSummariser` | Builds bounded context prompt → calls active `LlmProvider` → produces structured `IncidentSummary` |
| `SlackNotifier` | Posts incident to Slack webhook; deduped by `IncidentFingerprint` |
| DynamoDB | Stores `incidents`, `metrics_state`, `processed_events` (idempotency keys with TTL) |
| Grafana | 6-panel dashboard: throughput, error rate, p95 duration, anomaly scores, incidents by severity, LLM latency |

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Sealed interfaces for `LlmProvider`, `AnomalyDetector`, `Notifier` | Exhaustive pattern matching; swappable at startup via `application.yml` |
| LLM out of the critical path | Anomaly persists even if Gemini is down; incident written with `summary=null` tagged `llm_unavailable` |
| KRaft Kafka (no Zookeeper) | Saves ~300 MB Docker RAM on a 16 GB dev machine |
| Virtual threads on consumer executor | IO-bound workload; poll loop stays on platform thread (safe) |
| DynamoDB Local over Postgres | Schema-less, matches the AWS CDK stack, no migration tooling needed at this scope |
| EWMA z-score as baseline detector | 30 lines, zero dependencies, always warm — ships before Isolation Forest |
| `cdk synth` only, never `cdk deploy` | Zero AWS cost; IaC code in `infra/cdk/` is reference architecture only |
