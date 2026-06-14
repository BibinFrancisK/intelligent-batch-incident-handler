# Anomaly Detection Design

This document covers the feature engineering, algorithm design, cold-start behavior, and threshold tuning for the two anomaly detectors in `incident-intelligence-service`.

---

## Feature Engineering

Each `JobCompleted` event produces one `FeatureVector`. Five dimensions are scored; both detectors operate on the same vector.

| # | Feature | Type | Source | Purpose |
|---|---|---|---|---|
| 1 | `durationSeconds` | `double` | `JobCompleted.durationSeconds()` | Primary latency signal — nightly batch jobs have tight SLAs; a 2× slowdown is always worth investigating |
| 2 | `errorRate` | `double` | `sumErrorCount / rowsProcessed` (rolling) | Quality signal — a rising error fraction predicts downstream data issues before they surface |
| 3 | `rowCount` | `long` | `JobCompleted.rowsProcessed()` | Volume signal — a job that processes far fewer rows than expected may have silently skipped records |
| 4 | `hourOfDay` | `int` | `event.timestamp().atZone(UTC).getHour()` | Temporal pattern — batch jobs run at specific hours; an unusually-timed run is structurally different from a scheduled one |
| 5 | `dayOfWeek` | `int` | `event.timestamp().atZone(UTC).getDayOfWeek().getValue()` | Weekend vs weekday — Saturday ANNUITY_PAYOUT jobs are slower by design; without this feature, they would generate false positives |

> **EWMA note:** only `durationSeconds` is scored by the EWMA detector. The other four dimensions are present in the `FeatureVector` but unused by EWMA. They exist for Isolation Forest and to avoid a breaking interface change when the active detector is switched.

---

## EWMA Z-Score Detector

### Algorithm

```
μ_t  = α · x_t  + (1 - α) · μ_{t-1}            exponential mean
σ_t  = α · |x_t - μ_t| + (1 - α) · σ_{t-1}     exponential MAD
z    = (x_t - μ_t) / max(σ_t, ε)                z-score (ε = 1e-9 guards div-by-zero)

anomalous  if  |z| > threshold
```

### Parameter rationale

| Parameter | Default | Rationale |
|---|---|---|
| `alpha` | `0.2` | Effective window ≈ 1/α = 5 recent observations. Balances reactivity against noise sensitivity. Higher α reacts faster but raises false-positive rate on spiky-but-normal jobs. |
| `threshold` | `3.5` | Corresponds to ~3.5 standard deviations in a Gaussian model. In steady state, uniform ±1s jitter around a 10s mean produces max z ≈ 2.5, leaving a 1-sigma safety margin. |
| `ε` (epsilon) | `1e-9` | Prevents division by zero on the first scored observation (before σ has accumulated). Returns z=0 effectively, which is never anomalous. |

### State model

State is per-`JobType`, held in a `ConcurrentHashMap<JobType, EwmaState>`. Each entry is an immutable record `EwmaState(double mu, double sigma)` replaced atomically on every observation. In-memory only — EWMA self-corrects within ~10 observations of any drift, so persistence is not worth the complexity.

### Steady-state convergence

With α=0.2, σ converges to approximately `(1 - α) · MAD_true = 0.8 · MAD_true`. For uniform ±1s jitter, `MAD_true = 0.5s`, so steady-state σ ≈ 0.4s. The half-life of the transient from cold-start is `log(0.1) / log(1 - α) ≈ 10` observations. Assertions in `EwmaAnomalyDetectorTest` skip the first 30 observations to clear this window conservatively.

---

## Isolation Forest Detector

### Algorithm

An isolation tree randomly selects a feature dimension and a split value. Points that are easy to isolate (few splits needed) are anomalous; points in dense regions of the feature space require many splits and are normal.

Anomaly score from the original Liu et al. (2008) paper:

```
s(x, n) = 2^( -E[h(x)] / c(n) )

c(n) = 2 · H(n-1) - 2·(n-1)/n        normalisation factor (average path length for a binary tree of n points)
H(k) = ln(k) + 0.5772...              Euler–Mascheroni approximation of the harmonic number
```

Score interpretation: `s → 1` (anomaly) · `s ≈ 0.5` (normal) · `s → 0` (definitely normal).

### Design choices

| Parameter | Value | Rationale |
|---|---|---|
| `trees` | `100` | Standard recommendation from the paper. Variance in scoring drops below 1% beyond 100 trees. |
| `sample-size` | `256` | Default sub-sampling per tree. Larger values reduce variance but increase memory and training time. For batch-job metrics (low-cardinality, low-noise), 256 is more than sufficient. |
| Feature count | `5` | More dimensions dilute isolation scores because random splits spread across all dimensions. Five dimensions keeps the "curse of dimensionality" manageable while capturing the signals that matter. |
| Threshold | `0.65` (prod) · `0.6` (tests) | Normal points score ≈ 0.5; a point unusual in one dimension scores ≈ 0.65–0.72 depending on feature count. Prod uses 0.65 (tighter). Tests use 0.6 to account for random-seed variance across CI machines. |

### Retraining strategy

`ModelBootstrap` scans the DynamoDB `metrics_state` table on startup and trains a single model from the aggregate rolling metrics. Aggregate rows do not carry `hourOfDay` / `dayOfWeek` (those fields are padded with `0.0`). This is a deliberate trade-off: the model warms up on real historical volume and latency signals rather than cold-starting.

Periodic online retraining (e.g. nightly re-fit from the last 7 days of completed events) is a stretch goal. The `IsolationForestDetector.fit(double[][] data)` method is intentionally public to support it without interface changes.

---

## Cold-Start Behavior and Fallback Chain

```
App starts
    │
    ▼
incident.anomaly.detector=isolation-forest?
    ├── YES → ModelBootstrap.run()
    │           │
    │           ├── metrics_state has ≥ 10 rows → fit model → IsolationForest active
    │           │
    │           └── < 10 rows → WARN log → model stays null
    │                               │
    │                               ▼
    │                   IsolationForestDetector.score()
    │                       model == null → EwmaAnomalyDetector.score()  ← fallback
    │                           │
    │                           ├── state exists → compute z-score
    │                           └── no state → cold start (score=0, anomalous=false)
    │
    └── NO (ewma, default) → EwmaAnomalyDetector active
                                 │
                                 ├── state exists → compute z-score
                                 └── no state → cold start (score=0, anomalous=false)
```

When the Isolation Forest falls back to EWMA, the returned `AnomalyScore.reason()` contains `"isolation-forest not fitted — ewma fallback: ..."` so it is visible in structured logs without additional MDC context.

---

## Threshold Tuning Guidance

| Environment | Detector | Recommended threshold | Reasoning |
|---|---|---|---|
| Local dev | `ewma` | `3.5` (default) | DDB is empty on a fresh container; EWMA warms up quickly |
| Local dev | `isolation-forest` | `0.6` | Few training samples; model variance is higher |
| CI | `ewma` | `3.5` | Noop tests don't trigger scoring; threshold is irrelevant in CI |
| Production | `ewma` | `3.0`–`3.5` | Lower if false-negative rate is unacceptable for the SLA |
| Production | `isolation-forest` | `0.65`–`0.70` | Raise if alert volume is too high; lower if incidents are being missed |

**Tuning signals to watch in Grafana:**
- `anomaly_score` distribution summary — if the p95 of normal jobs is > 0.55, the threshold is too tight.
- `incidents_detected_total` counter by severity — a sudden spike usually means the threshold needs adjustment, not a real incident wave.
- EWMA: if the `sigma` printed in `AnomalyScore.reason()` is very small (< 0.05s), the job is highly periodic — alpha can be increased to track it more loosely.
