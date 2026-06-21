# Design Decisions

ADR-lite entries for the 7 key architectural choices made in this platform. Each entry captures the decision, the constraint that drove it, what was rejected, and what it costs.

---

### 1. KRaft Kafka (no Zookeeper)

**Decision:** Run Kafka in KRaft mode, eliminating the Zookeeper dependency entirely.

**Why:** The full local stack — Kafka, Prometheus, Grafana, DynamoDB Local, and the Spring Boot app — runs within a deliberate ~1.5 GB Docker footprint so the environment is reproducible on any developer workstation without requiring dedicated infrastructure. Zookeeper adds ~300 MB of heap and a separate JVM process for zero functional gain at single-broker scale. KRaft has been production-grade since Kafka 3.3 and is now the default in Kafka 4.x — adopting it aligns with the current ecosystem direction rather than carrying forward a legacy operational model.

**Alternatives considered:** Zookeeper-based Kafka (`confluentinc/cp-kafka` with a separate `cp-zookeeper` container) — the conventional multi-container deployment model. Rejected because it doubles broker startup complexity and costs ~300 MB RAM for zero functional benefit at single-node scale.

**Consequences:** The `CLUSTER_ID` must be pre-generated and baked into the Compose config (`CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"`). Any team member running a fresh clone must use the same ID or wipe the Kafka data volume. KRaft is also less battle-tested in Kafka versions below 3.3, though this project pins `confluentinc/cp-kafka:7.6.1`.

---

### 2. Sealed Interfaces for `LlmProvider`, `AnomalyDetector`, and `Notifier`

**Decision:** Model every pluggable strategy as a Java 17+ sealed interface with an exhaustive `permits` clause; select the active implementation at startup via `application.yml`.

**Why:** Sealed interfaces make the compiler an enforcer of correctness. Every `switch` expression over a sealed type is exhaustive — the compiler rejects missing cases at build time, not at runtime. This is a stronger contract than annotation-based polymorphism: when a new `LlmProvider` implementation is added, the compiler immediately flags every switch that needs updating. The `permits` clause also makes the complete set of valid implementations self-documenting at the type definition.

**Alternatives considered:**
- **`@Qualifier`-based Spring bean injection** — familiar to most Spring developers but purely convention-based; a missing case silently resolves to `null` or throws a `NoUniqueBeanDefinitionException` at startup, not at compile time.
- **`Strategy` enum with a factory method** — no compiler-level interface enforcement; requires explicit `default` handling.

**Consequences:** Adding a new implementation (e.g., `AnthropicProvider`) requires updating every `switch` statement over `LlmProvider` in addition to the `permits` clause. This is intentional — the cost is low and the benefit (compile-time completeness) outweighs it. Spring CGLIB proxying requires `non-sealed` on concrete implementations to allow subclassing for AOP (`@Observed`).

---

### 3. LLM Is Out of the Critical Path

**Decision:** Anomaly detection, DynamoDB persistence, and Slack notification all complete before — and independently of — the LLM call. If the LLM circuit-breaker is open, the incident is written with `summary=null` and tagged `llm_unavailable`.

**Why:** The Kafka consumer thread budget is finite. A 3-second LLM timeout would block consumer progress, push `max.poll.interval.ms`, and trigger a group rebalance — turning an LLM outage into a Kafka consumer outage. The incident data (anomaly score, job type, feature vector) is the primary artifact; the LLM summary is a human-readable enrichment, not the source of truth.

**Alternatives considered:** Block the consumer on the LLM call with a short timeout and fail the entire event on LLM error — rejected because it conflates an external API's availability with our own pipeline's reliability. The two concerns must be independently resilient.

**Consequences:** Incidents may be written with `summary=null` during LLM outages. There is no backfill mechanism in the current scope — incidents tagged `llm_unavailable` accumulate without summaries until the LLM recovers and the next anomaly triggers a new incident. A DLQ replay or backfill job would address this (stretch goal).

---

### 4. EWMA Z-Score Ships Before Isolation Forest

**Decision:** Implement the EWMA z-score detector first as the always-available baseline; add the Isolation Forest (Smile library) as the advanced detector behind the same `AnomalyDetector` sealed interface.

**Why:** EWMA z-score is 30 lines of code, zero dependencies, and warm from the first event. Isolation Forest requires a training corpus (minimum ~50 data points per job type) before its scores are meaningful — it is pathologically wrong on a cold start. Shipping the baseline first means the platform is operational and defensible from day one, even before enough data accumulates for the tree model.

**Alternatives considered:**
- **Isolation Forest only** — rejected due to cold-start problem; an untrained model produces arbitrary scores that would trigger false positives on a fresh deployment.
- **Static thresholds (e.g., duration > 5× baseline)** — too brittle; does not adapt to seasonal patterns or gradual drift.
- **Python ML sidecar** — rejected; adds a process boundary, serialization overhead, and operational complexity that is not justified for a 5-dimensional feature vector at low event frequency.

**Consequences:** EWMA threshold (default `|z| > 3.5`) is a univariate check per metric dimension — it does not detect correlated multi-dimensional anomalies (e.g., slightly elevated duration AND slightly elevated error rate together). Isolation Forest catches these, but only after the model has warmed up. Both detectors are available; the active implementation is selected via `application.yml` with no code changes required.

---

### 5. DynamoDB Local Over Postgres

**Decision:** Use DynamoDB Local (Docker) for local development and AWS DynamoDB (free tier) for the Terraform AWS deployment; no relational database.

**Why:** Three constraints aligned: (1) the Terraform stack targets AWS DynamoDB — using the same API locally means zero schema drift between environments; (2) DynamoDB's conditional `PutItem` with `attribute_not_exists` gives atomic idempotency at the partition level without serializable transactions; (3) schema-less storage avoids Flyway/Liquibase setup that would add an entire migration toolchain for a single-service, three-table scope.

**Alternatives considered:**
- **Postgres with `ON CONFLICT DO NOTHING`** — familiar, strong ACID guarantees, excellent tooling. Rejected because it requires Flyway or Liquibase for schema versioning, a JDBC connection pool, and a separate Postgres Docker service. The complexity budget is not justified here.
- **H2 in-memory (for local dev)** — rejected because it diverges entirely from the production DynamoDB API, producing a false sense of test coverage for persistence logic.

**Consequences:** No support for ad-hoc SQL queries, joins, or aggregations. All query patterns must be pre-modeled as DynamoDB access patterns. For this scope (three tables, key-value access patterns), this is not a constraint. If reporting or analytics were added, a read replica or event-sourced projection would be needed.

---

### 6. EC2 t3.micro Over Lambda for AWS Deployment

**Decision:** The Terraform stack deploys to an EC2 t3.micro instance running Docker Compose (Kafka, Prometheus, Grafana) alongside the Spring Boot app; AWS Lambda is explicitly excluded.

**Why:** The `BatchEventConsumer` is a long-running Kafka poll loop. Lambda's execution model is fundamentally incompatible: Lambda instances are stateless, idle after invocation, and capped at 15 minutes — none of which can host a persistent consumer group. EC2 is the correct primitive for a stateful, long-lived Kafka consumer.

**Alternatives considered:**
- **AWS Lambda triggered by MSK (Managed Streaming for Kafka)** — Lambda cannot maintain a consumer group across invocations; each invocation is a cold start with no offset continuity. Rejected.
- **ECS Fargate** — correct architecture for containerized long-running consumers; introduces VPC configuration, task definitions, service discovery, and load balancer setup that would shift the scope from application concerns to infrastructure orchestration.
- **EKS** — correct for production multi-tenant scale; out of scope by design — the platform value is in the event pipeline and observability layer, not in infrastructure distribution.

**Consequences:** EC2 t3.micro accrues ~$0.01/hr. `terraform destroy` must be run immediately after each deployment cycle to avoid ongoing charges. The Terraform stack is designed for short-lived proof-of-concept deployments — it is not a production-hardened configuration (no autoscaling, no multi-AZ, no secrets manager integration).

---

### 7. Virtual Threads on the Kafka Consumer Executor

**Decision:** Configure the Kafka listener container to use a virtual-thread executor (Project Loom, Java 21) for dispatching record processing; the Kafka poll loop itself remains on a platform thread.

**Why:** Record processing is IO-bound: each event triggers a DynamoDB conditional write (idempotency check), a DynamoDB update (metrics accumulation), optionally an HTTP call to Gemini, and an HTTP POST to Slack. With platform threads, each of these blocks a carrier thread for the duration of the IO. Virtual threads park during blocking IO and release the carrier thread — the JVM schedules hundreds of concurrent IO operations on a handful of OS threads without explicit thread pool tuning.

**Alternatives considered:**
- **Fixed platform thread pool (e.g., `Executors.newFixedThreadPool(10)`)** — requires manual sizing; too small causes backpressure, too large wastes OS resources. Rejected in favor of the JVM managing scheduling automatically.
- **Reactive/WebFlux pipeline** — correct approach for very high throughput, but requires a fully non-blocking call chain including non-blocking DynamoDB SDK and non-blocking HTTP. At this event frequency and with synchronous LangChain4j, the reactive overhead is not justified.

**Consequences:** The Kafka poll loop must stay on a platform thread — `max.poll.interval.ms` is enforced by a wall-clock deadline on the poll thread, and virtual-thread scheduling is cooperative rather than preemptive. Dispatching the poll loop itself to a virtual thread risks starvation under high record volume. This split (platform thread for poll, virtual threads for processing) is intentional and documented in `KafkaConfig.java`.
