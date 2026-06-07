# Monitoring and observability

> Like [deployment.md](deployment.md), this is the **operational design** a senior engineer would put forward if this pipeline were productionized. Nothing in the current repo emits metrics or traces; this document is the contract you'd hold the implementation to.

The right time to think about observability is when designing the system, not after the first incident. EDI pipelines have specific failure shapes that generic web-app dashboards miss.

---

## What you actually want to know

For an EDI pipeline, three questions matter at 2am:

1. **Is data flowing?** Stuck queues are silent and lethal.
2. **Is data correct?** A pipeline emitting consistent garbage is worse than one that crashes.
3. **Where in the pipeline is the bottleneck?** Capacity planning and incident response both need this.

Generic dashboards don't answer these. Build for them specifically.

---

## The three signals

### 1. Logs (structured, queryable)

Every stage emits **structured JSON logs**. One log line per processed unit (file, transaction, claim). Every line carries the correlation key.

```json
{
  "ts": "2026-06-06T12:34:56.789Z",
  "level": "INFO",
  "service": "x12-parser",
  "version": "0.1.0",
  "interchange_control_number": "000000001",
  "transaction_set_control_number": "0001",
  "transaction_set_id": "837",
  "event": "transaction_parsed",
  "segment_count": 12,
  "duration_ms": 14
}
```

Required fields on every line:

| Field | Why |
|---|---|
| `ts` | obvious; ISO-8601 with ms |
| `level` | filter on triage |
| `service` | parser / transformer / loader |
| `version` | which deploy produced this |
| `interchange_control_number` | top-level correlation across the whole pipeline |
| `transaction_set_control_number` | per-claim correlation |
| `event` | one of a small enumeration; queryable |

Errors add `error_type`, `error_message`, and a stack trace.

**What NOT to log:**

- PHI. Ever. Member names, member IDs, claim diagnoses — all forbidden. Hashes are also forbidden (rainbow-table attackable on small ID spaces). Use opaque internal claim IDs derived at ingest.
- The entire EDI payload on error. Log the segment id and element position; pull the payload from the object store under audit if needed.

**Log shipping**: stdout from containers → fluent-bit / Vector → centralized store (Datadog, Loki, Splunk). Retention: 90 days hot, 6 years cold for HIPAA audit purposes (separate retention policy from the main app logs).

### 2. Metrics

RED method per stage (rate, errors, duration), plus pipeline-specific gauges.

#### Per-stage RED metrics

| Metric | Type | Labels |
|---|---|---|
| `x12_transactions_processed_total` | counter | `service`, `transaction_set_id`, `status` (`parsed`/`failed`/`quarantined`) |
| `x12_processing_duration_seconds` | histogram | `service`, `transaction_set_id` |
| `x12_processing_errors_total` | counter | `service`, `error_type` |

Histograms (not just averages) — the latency distribution matters more than the mean for capacity planning, and tail latencies hide in averages.

#### Pipeline-specific gauges

| Metric | What it tells you |
|---|---|
| `x12_queue_depth` (per queue) | Backpressure / stuck consumer |
| `x12_oldest_unprocessed_age_seconds` | A queue with 100 items aged 30s is fine; with 5 items aged 4 hours is a stuck consumer with a poison message |
| `x12_validation_errors_total{rule=…}` | Sudden spike per-rule means a clearinghouse changed something — or your rule broke |
| `x12_claim_total_charge_dollars_sum` | Sanity check; sums should be roughly stable day-over-day for the same payer |
| `basex_query_duration_seconds{query_name=…}` | Per-query latency; degradation usually means missing/stale indexes |
| `basex_document_count` | Should grow monotonically; a drop is a sign that something is deleting more than it adds |

Emission: Prometheus client libraries in each runtime (`prometheus-cl` for SBCL, `iapetos` for Clojure). Scraped by Prometheus → Grafana dashboards.

### 3. Traces

OpenTelemetry, span per stage. The valuable thing is a single trace that spans **parse → transform → load**, keyed by interchange + transaction control number.

```
trace: ic=000000001, ts=0001
└── span: parse                            [42 ms]
    └── span: validate                     [3 ms]
└── span: transform                        [18 ms]
    └── span: malli-validate               [1 ms]
└── span: persist-to-basex                 [85 ms]
```

When a claim is "stuck", the trace shows which stage didn't see it (or where it failed). Without traces, this is grep-the-logs forensics across multiple services.

OTLP exporters exist for both runtimes. The Clojure side is straightforward; the SBCL side is less mature — minimum viable is HTTP-export to an OTLP collector.

---

## Dashboards

Three permanent ones; not many more.

### Pipeline health

The single page on-call looks at first.

- **Volume sparkline**: transactions per minute, last 24h, color-banded by stage.
- **Stage error rates**: small-multiples bar chart, one panel per stage.
- **Queue ages**: line per queue, log scale.
- **Top error types this hour**: top-10 table.

### Per-clearinghouse quality

Validation errors broken down by sender. A clearinghouse sending consistently malformed data is a business conversation, not a tech issue — but you need data to have it.

- Validation-error rate per `interchange_sender_id`, last 7 days.
- New error types per sender (anomaly detection).
- Top-10 failing segment types per sender.

### BaseX / MarkLogic health

- Query latency p50/p95/p99 per query.
- Document count growth.
- Disk usage trend.
- Index hit rate (range index hits vs. full scans).

---

## Alerting

The rule: alert on **user-visible problems**, not on conditions. If a metric being out of bounds doesn't directly imply a person is affected, it's a dashboard, not an alert.

| Alert | Threshold | Why |
|---|---|---|
| Pipeline stalled | `x12_oldest_unprocessed_age_seconds > 600` for 5 min | Claims aren't reaching the next stage |
| Parse error spike | error rate > 5× 7-day baseline for 15 min | Either a deploy regressed or a sender's format changed |
| BaseX query degradation | p99 query latency > 5s for 10 min | Index rebuild needed or hardware issue |
| Quarantine queue growing | depth > 100 AND growing | Bad data accumulating; needs human triage |
| End-to-end e2e canary failed | synthetic claim takes > 60s OR fails | The pipeline itself is broken |
| Disk > 85% on BaseX nodes | once | Capacity action needed |

Everything else is a dashboard. PagerDuty / Opsgenie integration; rotate on-call weekly; runbook URL on every alert.

### Runbooks

Every alert has a runbook entry. The minimum:

1. **Symptom**: how the alert presents.
2. **Likely causes**, ranked by frequency.
3. **Diagnostic queries**: pre-written log/metric searches.
4. **Rollback / mitigate**: how to stop the bleeding before fixing.
5. **Escalation**: who owns this if the on-call can't fix in 30 min.

Runbook entries live next to the code, in `docs/runbooks/`. (Not in this repo; not in scope for the portfolio.)

---

## Synthetic monitoring

A canary 837D file with a known transaction control number is dropped into the ingest path every minute. The pipeline should produce a corresponding BaseX document within N seconds. If it doesn't:

- Alert fires.
- The trace for the canary shows where it stopped.
- The dashboard isolates which stage owns the gap.

This is the single highest-leverage piece of monitoring on an EDI pipeline. It catches:

- Misconfigured queue bindings (manifests as silent loss).
- Schema drift between stages (silent loss again).
- Slow degradation that's per-message imperceptible.

The canary EDI file is checked into the repo (synthetic, fake data) and the monitoring infrastructure drops a copy every minute. Result: continuous deploy + canary = sub-minute notification of regressions.

---

## SLOs

| SLO | Target |
|---|---|
| 99.5% of 837 claims arrive in BaseX within 5 minutes of receipt at the SFTP dropzone | rolling 30 days |
| 99.9% of parse attempts succeed (or quarantine cleanly, not crash) | rolling 30 days |
| BaseX `eligibility-lookup` p95 < 200ms | rolling 7 days |
| Zero PHI in logs | absolute; CI gate scans for PII patterns in log statements |

The error budgets these imply are the source of "should we deploy this?" decisions, not abstract risk-aversion.

---

## What this repo does today

| Signal | Status |
|---|---|
| Structured JSON logs | ✅ Project 06: logback + logstash encoder, every line carries `request_id` and `tenant_id` via MDC |
| Audit log | ✅ Project 06: dedicated `audit` logger routed to its own appender (separate retention in prod) |
| Metrics | ✅ Project 06: iapetos + Prometheus at `/metrics` (JVM defaults + HTTP request latency + adjudis-specific: auth attempts by outcome, adjudications by tenant/verdict, findings by category/severity, adjudication duration histogram) |
| Correlation IDs | ✅ Project 06: `X-Request-Id` honored or generated per request, echoed in response, propagated through MDC |
| Traces | Not yet. OpenTelemetry sketched in the per-stage section above; implementation is a follow-up commit. |
| Dashboards | Not in this repo (they live in Grafana). Prometheus metrics shape supports the dashboards described above. |
| Alerts | Not in this repo (Alertmanager rules). Thresholds documented above. |
| Canary | None (but `make e2e` and `make adjudicate-demo` are manual canaries that could trivially be cron'd). |

Projects 01–05 don't yet emit structured logs / metrics; they're CLI tools and the observability story attaches at the orchestrator that runs them. The interesting observability work in this repo lives in project 06 (the HTTP service).

---

## See also

- [architecture.md](architecture.md) — the system being observed
- [deployment.md](deployment.md) — where the observability infrastructure lives
- [onboarding.md](onboarding.md) — local development
