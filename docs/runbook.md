# Runbook — incident-intelligence-service

Operational reference for local dev, AWS demo deployment, and CI recovery.

---

## Local Development

### Start infra

```bash
docker compose -f docker/docker-compose.yml up -d
# Wait ~30s for Kafka healthcheck to pass
```

### Start the app

```bash
export GEMINI_API_KEY=<your-key>        # omit if incident.llm.provider=noop
export SLACK_WEBHOOK_URL=<your-webhook> # omit if incident.notify.provider=noop
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Inject an anomaly

A single command publishes 10 warm-up events to seed the EWMA baseline, then one anomalous run — same JVM, same Kafka partition, guaranteed ordering:

```bash
./mvnw exec:java \
  -Dexec.mainClass="io.batchintel.simulator.BatchSimulatorRunner" \
  -Dspring.profiles.active=local \
  -Dspring.main.web-application-type=none \
  "-Dexec.args=--jobType=ANNUITY_PAYOUT --anomaly=true"
```

### Stop everything

```bash
# Stop app: Ctrl+C in the mvnw terminal
docker compose -f docker/docker-compose.yml down -v
```

### Service URLs

| Service | URL | Credentials |
|---|---|---|
| App health | `http://<host-name>:8080/actuator/health` | — |
| Prometheus metrics | `http://<host-name>:8080/actuator/prometheus` | — |
| Prometheus UI | `http://<host-name>:9090` | — |
| Grafana | `http://<host-name>:3000` | admin / admin |
| DynamoDB Local | `http://<host-name>:8000` | dummy / dummy |

---

## AWS Demo Deployment

> **Cost reminder:** EC2 t3.micro accrues ~$0.01/hr. Run `terraform destroy` as soon as testing is complete.

### Prerequisites

```bash
# AWS credentials
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>
export AWS_DEFAULT_REGION=us-east-1

# Local SSH key pair (create if missing)
ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""
```

### Deploy

```bash
cd infra/terraform
terraform init
terraform apply -var="environment=demo"
# Type "yes" at the prompt.
# Wait ~90 seconds for EC2 user-data bootstrap to complete.

EC2_IP=$(terraform output -raw ec2_public_ip)
echo "EC2: $EC2_IP"
```

### Configure and start the app on EC2

```bash
# Copy the project to EC2
scp -r ../../ ec2-user@$EC2_IP:~/intelligent-batch-incident-handler

# SSH in
ssh ec2-user@$EC2_IP

# Inside EC2:
cd ~/intelligent-batch-incident-handler
export GEMINI_API_KEY=<your-key>
export SLACK_WEBHOOK_URL=<your-webhook>
export AWS_DEFAULT_REGION=us-east-1

# Start Kafka, Prometheus, Grafana
docker-compose -f docker/docker-compose.yml up -d kafka prometheus grafana

# Start the Spring Boot app (aws profile disables DynamoDB Local endpoint)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,aws
```

### Test on AWS

Run from your local machine (in a second terminal):

```bash
EC2_IP=$(cd infra/terraform && terraform output -raw ec2_public_ip)

# Health check
curl -s http://$EC2_IP:8080/actuator/health | grep '"status":"UP"'

# Warm-up (run 5 times)
./mvnw exec:java -Dexec.mainClass="io.batchintel.simulator.BatchSimulatorRunner" \
  -Dspring.profiles.active=local \
  -Dexec.args="--jobType=ANNUITY_PAYOUT"

# Inject anomaly
./mvnw exec:java -Dexec.mainClass="io.batchintel.simulator.BatchSimulatorRunner" \
  -Dspring.profiles.active=local \
  -Dexec.args="--jobType=ANNUITY_PAYOUT --anomaly=true"

# Verify incident in real AWS DynamoDB
aws dynamodb scan --table-name demo-incidents --region us-east-1

# Verify Prometheus counter
curl -s http://$EC2_IP:8080/actuator/prometheus | grep incidents_detected

# Open Grafana
echo "http://$EC2_IP:3000  (admin/admin)"
```

### Destroy

```bash
# Exit the EC2 SSH session first, then from your local machine:
cd infra/terraform
terraform destroy -var="environment=demo"
# Type "yes" at the prompt.

# Verify EC2 is gone
curl --connect-timeout 5 http://$EC2_IP:8080/actuator/health  # must time out

# Verify DynamoDB tables are gone
aws dynamodb list-tables --region us-east-1  # demo-incidents must not appear
```

---

Assumes Docker Compose infra is running locally (`docker compose -f docker/docker-compose.yml up -d`).

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
  --endpoint-url http://<host-name>:8000 \
  --select COUNT

# View full dedupe table (small volumes only)
aws dynamodb scan \
  --table-name processed_events \
  --endpoint-url http://<host-name>:8000
```

### Inspect rolling metrics

```bash
# Full metrics_state table — one row per jobType
aws dynamodb scan \
  --table-name metrics_state \
  --endpoint-url http://<host-name>:8000

# Single jobType row
aws dynamodb get-item \
  --table-name metrics_state \
  --endpoint-url http://<host-name>:8000 \
  --key '{"jobType":{"S":"ANNUITY_PAYOUT"}}'
```

### Inspect incidents

```bash
aws dynamodb scan \
  --table-name incidents \
  --endpoint-url http://<host-name>:8000
```

---

## Kafka

### Check DLQ depth

```bash
docker exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list <host-name>:9092 \
  --topic batch.events.v1.dlq
```

### Tail the incidents topic

```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server <host-name>:9092 \
  --topic incidents.v1 \
  --from-beginning
```

### Reset consumer group offsets (re-consume from beginning)

Use this to replay all events through the metrics pipeline in testing.

```bash
# Stop the Spring Boot app first, then:
docker exec kafka kafka-consumer-groups \
  --bootstrap-server <host-name>:9092 \
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
curl -s http://<host-name>:8080/actuator/prometheus | grep batch_

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
