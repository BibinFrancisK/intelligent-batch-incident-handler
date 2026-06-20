# Skill: Security Review

## Purpose

Perform a targeted security review of pending changes on the current branch against the threat model for this event-driven platform. Scope covers the application layer — Kafka consumer, anomaly detection, LLM integration, DynamoDB persistence, and Slack notification.

---

## When to Invoke

- Before opening a PR that touches `kafka/`, `llm/`, `persistence/`, or `notify/`
- When adding or changing Kafka error handling, DLQ routing, or retry logic
- When modifying `IncidentSummarizer`, prompt templates, or LLM provider wiring
- When changing how secrets (`GEMINI_API_KEY`, `SLACK_WEBHOOK_URL`) are read or used
- When modifying DynamoDB write paths or idempotency logic

---

## What This Skill Checks

See `checklist.md` in this directory for the full itemized list. At a high level:

1. **Event deserialization safety** — `BatchEvent` JSON is deserialized with `FAIL_ON_UNKNOWN_PROPERTIES=false`; malformed events route to DLQ, never crash the consumer
2. **Idempotency correctness** — DynamoDB conditional `PutItem` uses `attribute_not_exists(eventId)`; duplicates are silently skipped, not double-processed
3. **LLM prompt injection** — incident context passed to the LLM is bounded and sanitized; no raw event payload strings are interpolated into the system prompt
4. **Secrets handling** — `GEMINI_API_KEY` and `SLACK_WEBHOOK_URL` are read from environment variables only; never logged, hardcoded, or returned in responses
5. **Circuit breaker coverage** — LLM and Slack calls are wrapped in Resilience4j circuit breakers; a provider outage must not block incident persistence
6. **DLQ safety** — poison messages land in `batch.events.v1.dlq` after max retries; the DLQ consumer never re-throws in a way that causes infinite reprocessing
7. **Retry header integrity** — `x-retry-attempt` header is incremented correctly; a missing header defaults to attempt 0, not a skip
8. **Dependency hygiene** — no new dependencies with known CVEs; `./mvnw dependency:check` is clean

---

## What This Skill Does NOT Cover

- Authentication / authorization — explicitly out of scope
- HTTPS / TLS — terminated at the load balancer in AWS; HTTP is acceptable locally
- AWS IAM policies — covered by the Terraform stack in `infra/terraform/`, not reviewed here
- Grafana or Prometheus access control — admin/admin is acceptable for local dev only

---

## How to Run

1. Check out the branch under review
2. Run `git diff main...HEAD --name-only` to identify changed files
3. Work through `checklist.md` item by item against the diff
4. Report each finding with: file, line, risk level (LOW / MEDIUM / HIGH), and recommended fix
5. If all items pass, confirm clean and summarize

---

## Output Format

```
## Security Review — <branch-name>

### Findings
| # | File | Line | Risk | Issue | Recommendation |
|---|------|------|------|-------|----------------|
| 1 | ... | ... | HIGH | ... | ... |

### Passed Checks
- [x] Secrets not hardcoded
- [x] LLM call behind circuit breaker
- ...

### Verdict
PASS / FAIL — <one sentence summary>
```
