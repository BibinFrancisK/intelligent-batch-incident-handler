# Changelog

All notable changes to this project are documented here.

---

## [v1.0.0] — 2026-06-21

### Added

- **Kafka event pipeline** — `batch.events.v1` with retry topic and DLQ; at-least-once delivery, idempotent consumer via DynamoDB conditional `PutItem`
- **Dual anomaly detection** — EWMA z-score (zero-dependency baseline, warm from first event) and Isolation Forest (Smile library) behind a sealed `AnomalyDetector` interface; active implementation selected via `application.yml`
- **LLM incident summaries** — LangChain4j → Gemini 1.5 Flash; bounded-context prompt; Resilience4j circuit breaker keeps the consumer unblocked if Gemini is unavailable
- **Slack notification** — fingerprint-based dedup (jobType + severity + hour); circuit breaker on the webhook path; fingerprint cleared on POST failure to allow retry
- **DynamoDB persistence** — `incidents`, `metrics_state`, `processed_events` tables; DynamoDB Local for dev, AWS DynamoDB via Terraform for demo
- **Grafana observability** — 6-panel dashboard: event throughput, p95 duration, error rate, anomaly scores, incidents by severity, LLM latency; provisioned automatically from `docker/grafana/dashboards/`
- **OpenTelemetry traces** — `traceId`/`spanId` injected into MDC; JSON log output via Logback
- **Terraform IaC** — EC2 t3.micro + DynamoDB; `apply`/`destroy` lifecycle verified
- **GitHub Actions CI** — `./mvnw verify` on every push; Testcontainers (Kafka + DynamoDB Local)
- **Java 21 virtual threads** — Kafka consumer executor; poll loop stays on platform thread
- **Sealed interfaces** — `LlmProvider`, `AnomalyDetector`, `Notifier`, `BatchEventType`; exhaustive compiler-enforced pattern matching

### Fixed

- EWMA cold-start false positives — added `MIN_WARM_UP_OBSERVATIONS=10` guard; prevents sigma=0 division from producing spurious anomalies on the second event
- Slack fingerprint held after POST failure — fingerprint now removed on exception so the next incident can retry in the same hour window
- Gemini timeout raised from 3s to 15s (configurable) — cold-start API latency regularly exceeded 3s

### Architecture decisions

See [`docs/decisions.md`](docs/decisions.md) for ADR-lite entries on: KRaft Kafka, sealed interfaces, LLM out of the critical path, EWMA-first detector ordering, DynamoDB over Postgres, EC2 over Lambda, and virtual threads.
