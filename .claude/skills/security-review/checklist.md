# Security Review Checklist

Use this checklist when reviewing any PR. Mark each item PASS / FAIL / N/A with a note if failed.

---

## 1. Event Deserialization Safety

- [ ] `BatchEvent` Jackson ObjectMapper has `FAIL_ON_UNKNOWN_PROPERTIES` set to `false` — new producer fields do not crash the consumer
- [ ] Malformed JSON (parse failure) is caught and routed directly to the DLQ — it does not trigger the retry loop
- [ ] No user-supplied or external string is passed to `Class.forName()`, `Runtime.exec()`, or reflection APIs
- [ ] `BatchEventType` sealed interface handles all known subtypes — unknown types are rejected at deserialization, not silently cast

---

## 2. Idempotency and DynamoDB Write Safety

- [ ] Idempotency `PutItem` uses `attribute_not_exists(eventId)` condition — a duplicate write throws `ConditionalCheckFailedException`, not a silent overwrite
- [ ] `ConditionalCheckFailedException` is caught and treated as "already processed" — it does not trigger retry or DLQ routing
- [ ] DynamoDB endpoint URL is read from `aws.dynamodb.endpoint` config — never hardcoded (Local vs AWS is controlled by profile)
- [ ] TTL on `processed_events` table is set — stale idempotency keys expire and do not grow unbounded

---

## 3. LLM Prompt Injection

- [ ] `IncidentContext` fields passed to the LLM are bounded in length (≤2 KB total) before prompt construction
- [ ] No raw `BatchEvent` payload string is interpolated directly into the system prompt
- [ ] `PromptTemplates` separates system instructions from user-supplied context — they are never concatenated into a single string
- [ ] LLM response is parsed via a structured JSON schema — raw LLM text is never trusted or forwarded as-is

---

## 4. Secrets Handling

- [ ] `GEMINI_API_KEY` is read from environment variable only — not present in any source file, log output, or API response
- [ ] `SLACK_WEBHOOK_URL` is read from environment variable only — not hardcoded or logged
- [ ] `docker/.env` is listed in `.gitignore` — only `docker/.env.example` (with placeholder values) is committed
- [ ] No secret values appear in Micrometer metric tags, MDC fields, or structured log output
- [ ] AWS credentials (if used) are provided via the default credential chain — never hardcoded

---

## 5. Circuit Breaker and Resilience Coverage

- [ ] All LLM provider calls are wrapped in a Resilience4j circuit breaker — an open circuit writes `incident.summary=null`, tagged `llm_unavailable`
- [ ] All Slack webhook calls are wrapped in a Resilience4j circuit breaker or retry policy
- [ ] Circuit breaker open state does not block the Kafka consumer — the consumer continues processing events
- [ ] Timeout is configured on LLM calls (≤3s) — a slow provider does not hold a virtual thread indefinitely

---

## 6. Kafka DLQ and Retry Safety

- [ ] `x-retry-attempt` header is read with a safe default of `0` — a missing header does not cause a `NullPointerException` or skip the retry count
- [ ] Max retry attempts is enforced via the header counter — it is not possible to exceed the configured limit
- [ ] The DLQ consumer never re-publishes to the main topic — it only logs and persists
- [ ] `DefaultErrorHandler` is configured with a non-retryable exception list for poison message types (e.g. `JsonParseException`)

---

## 7. Slack Notification Safety

- [ ] Slack notification payload does not include raw stack traces or internal system details
- [ ] Incident deduplication via `IncidentFingerprint` prevents the same anomaly from flooding the Slack channel
- [ ] Slack webhook URL is validated as non-empty on startup when `incident.notify.provider=slack` is active

---

## 8. Dependency Hygiene

- [ ] No new Maven dependencies with known CVEs — check with `./mvnw dependency-check:check` or review manually
- [ ] No use of `@SuppressWarnings("unchecked")` on security-relevant deserialization paths
- [ ] Testcontainer images are pinned to exact versions — no `latest` tags in test infrastructure

---

## Risk Level Definitions

| Level | Description |
|-------|-------------|
| HIGH | Could lead to data loss, prompt injection, secret leakage, or uncontrolled LLM spend |
| MEDIUM | Could lead to double-processing, alert spam, or unexpected consumer behaviour |
| LOW | Minor hygiene issue; no immediate exploitability |
