# Runbook — incident-intelligence-service

Operational reference for local dev and CI recovery. Assumes Docker Compose infra is running (`docker compose -f docker/docker-compose.yml up -d`).

---

## DynamoDB Local

### Wipe and recreate tables

DynamoDB Local runs `-inMemory`: all tables are lost on container restart. The `DynamoTableInitializer` (`@Profile("local")`) recreates them automatically on the next app boot.

```bash
# Restart DynamoDB Local (wipes all tables)
docker compose -f docker/docker-compose.yml restart dynamodb

# Then restart the app so DynamoTableInitializer recreates the tables
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Logs: "Created table processed_events / metrics_state / incidents"
```

### Inspect dedupe state

```bash
# Count rows in the idempotency table (should equal unique events processed)
aws dynamodb scan \
  --table-name processed_events \
  --endpoint-url http://localhost:8000 \
  --select COUNT

# View full dedupe table (small volumes only)
aws dynamodb scan \
  --table-name processed_events \
  --endpoint-url http://localhost:8000
```

### Inspect rolling metrics

```bash
# Full metrics_state table — one row per jobType
aws dynamodb scan \
  --table-name metrics_state \
  --endpoint-url http://localhost:8000

# Single jobType row
aws dynamodb get-item \
  --table-name metrics_state \
  --endpoint-url http://localhost:8000 \
  --key '{"jobType":{"S":"ANNUITY_PAYOUT"}}'
```

### Inspect incidents

```bash
aws dynamodb scan \
  --table-name incidents \
  --endpoint-url http://localhost:8000
```

---

## Kafka

### Check DLQ depth

```bash
docker exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic batch.events.v1.dlq
```

### Tail the incidents topic

```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic incidents.v1 \
  --from-beginning
```

### Reset consumer group offsets (re-consume from beginning)

Use this to replay all events through the metrics pipeline in testing.

```bash
# Stop the Spring Boot app first, then:
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group incident-intelligence \
  --reset-offsets \
  --to-earliest \
  --topic batch.events.v1 \
  --execute
```

---

## Metrics inspection

```bash
# Confirm Prometheus scrape endpoint is up
curl -s http://localhost:8080/actuator/prometheus | grep batch_

# Expected output includes:
# batch_events_processed_total{jobType="ANNUITY_PAYOUT",eventType="JobCompleted"} ...
# batch_job_duration_seconds_count{jobType="ANNUITY_PAYOUT"} ...
# batch_job_error_rate{jobType="ANNUITY_PAYOUT"} ...
```

---

## Common failures

| Symptom | Cause | Fix |
|---|---|---|
| `ResourceNotFoundException: non-existent table` | DDB Local restarted, app not rebooted | Restart app — `DynamoTableInitializer` recreates tables on boot |
| `UnrecognizedClientException` from DDB | SDK requires non-null credentials even for Local | `DynamoDbConfig` must use `StaticCredentialsProvider` with values from properties |
| `NoUniqueBeanDefinitionException: IdempotencyStore` | Both impls active, `@ConditionalOnProperty` typo | Dynamo: `matchIfMissing=true`; InMemory: `havingValue="memory"`; test profile sets `memory` |
| Metrics double-counted on replay | `extract()` called before idempotency guard | Call `metricsExtractor.extract()` after `idempotencyStore.isNew()` returns `true` |
| `metrics_state` `ADD` fails with type error | Attribute first written as String | Restart `dynamodb` container to wipe state; first write must use `AttributeValue.fromN` |
| No `traceId`/`spanId` in logs | Tracing bridge missing or observation not active | Confirm `micrometer-tracing-bridge-otel` on classpath; set `spring.kafka.listener.observation-enabled: true` |
| Logs still plain text | `logback-spring.xml` shadowed or wrong filename | Must be `src/main/resources/logback-spring.xml`; remove any `logback.xml` that shadows it |
