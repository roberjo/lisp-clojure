# Deployment

> **Scope honesty.** This repo is a portfolio. Nothing is deployed; nothing in here is binding on a real system. This document is the deployment design a senior engineer would propose IF this pipeline were productionized for a real EDI workload — it shows the thinking, not finished infrastructure.

The thinking matters because EDI processing is operationally non-trivial: clearinghouse SLAs, HIPAA compliance, batch sizes that swing 100×, downstream adjudication systems that hate retries. A correct local implementation says nothing about whether you understand the operational picture.

---

## Productionization shape

### Target topology

```
                  ┌─────────────────────┐
                  │ SFTP/AS2 dropzone   │  ← clearinghouse pushes
                  │ (EDI batches)       │
                  └──────────┬──────────┘
                             │ ingest poller
                             ▼
                  ┌─────────────────────┐
                  │ object store        │  S3 / Azure Blob.
                  │ /raw/<date>/<id>.edi│  WORM-locked for HIPAA.
                  └──────────┬──────────┘
                             │ "new file" event
                             ▼
                  ┌─────────────────────┐
                  │ parser worker (CL)  │  one instance per file.
                  │ project 02 + 03     │  emits structured plist
                  └──────────┬──────────┘
                             │ message queue
                             ▼
                  ┌─────────────────────┐
                  │ transform workers   │  N instances, autoscaled.
                  │ project 04 (JVM)    │  emits one JSON / claim
                  └──────────┬──────────┘
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
       ┌─────────────────┐    ┌─────────────────┐
       │ MarkLogic /     │    │ analytics sink  │
       │ BaseX cluster   │    │ (warehouse)     │
       │ project 05      │    │                 │
       └─────────────────┘    └─────────────────┘
```

Notably absent: a single "EDI service" that does everything in one process. Each stage scales independently, fails independently, and can be replaced independently.

### Packaging per project

| Project | Artifact | How built |
|---|---|---|
| 02 + 03 (CL) | `parser` binary (SBCL core image with the loaded systems) | `sb-ext:save-lisp-and-die` from a build script — startup goes from ~300ms to <50ms |
| 04 (Clojure) | uberjar | `clojure -T:build uber` (would add `build.clj` + `tools.build`) |
| 05 (XQuery) | `.xqy` files in the deploy bundle + index-setup script | filesystem deploy onto MarkLogic; or REST-deploy |
| Python bridge | single-file `from-json.py` | shipped as-is, with `python:3.12-slim` base image |

Container images are the natural unit; one Dockerfile per stage, sharing nothing except a base image.

### Runtime

- **CL parser**: long-running. Reads from queue, parses, emits to next queue. Memory bound is one EDI file at a time (call it 10–100MB upper); CPU bound on segment-split.
- **Clojure transformer**: long-running JVM. Pre-warmed, stays hot. Horizontal scale by partition key (interchange control number → consistent hash).
- **BaseX / MarkLogic**: persistent cluster, replicated. Read-heavy; writes via bulk `ADD` from the transformer.

The Python bridge isn't a runtime component in production — it's a debugging tool. Production would emit XML directly from Clojure (data.xml is mature enough, and the namespace boilerplate is a one-time cost).

---

## Configuration

### Twelve-factor where it pays

| Config | Source | Why |
|---|---|---|
| Queue endpoints, credentials | env vars + secrets manager | rotates per-env |
| BaseX / MarkLogic connection | env vars | per-env |
| Log level | env var `LOG_LEVEL` | tunable without redeploy |
| Feature flags (e.g. "enable 835 parsing") | dynamic config service | toggle without redeploy |
| Hardcoded today | the segment definitions in [`02-x12-parser/src/segments-837d.lisp`](../02-x12-parser/src/segments-837d.lisp) | spec is part of the code; "configuring" segment definitions at runtime would be a bug |

### Secrets

- **Never in env vars persisted to disk.** Use the platform's secrets manager (AWS Secrets Manager, Azure Key Vault) with IAM-scoped read.
- **PHI in transit**: TLS everywhere; SFTP requires SSH keys, not passwords; queue messages are encrypted at rest by the broker.
- **PHI at rest**: object store buckets and BaseX/ML databases are encrypted with customer-managed keys.

---

## CI/CD

### Pipeline

```
git push ─▶ CI ─┬─▶ make test          (Linux runner with full toolchain)
                ├─▶ make e2e            (smoke test)
                ├─▶ container builds    (parallel per project)
                └─▶ image scan          (Trivy or equivalent)
                        │
                        ▼
                  publish to ECR / GHCR
                        │
                        ▼
                  deploy to staging      (auto, on main)
                        │
                        ▼
                  manual approval gate
                        │
                        ▼
                  deploy to prod
```

A starter `.github/workflows/test.yml` is the natural next add to this repo. Outline:

```yaml
name: test
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install SBCL + Quicklisp
        run: |
          sudo apt-get install -y sbcl
          curl -O https://beta.quicklisp.org/quicklisp.lisp
          sbcl --non-interactive --load quicklisp.lisp \
               --eval '(quicklisp-quickstart:install)' \
               --eval "(let ((ql-util::*do-not-prompt* t))(ql:add-to-init-file))"
      - uses: DeLaGuardo/setup-clojure@13.1
        with: { cli: 1.12.5.1517 }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - name: Install BaseX
        run: |
          curl -L -o basex.zip https://files.basex.org/releases/12.0/BaseX120.zip
          sudo unzip -q basex.zip -d /opt
          sudo ln -sf /opt/basex/bin/basex /usr/local/bin/basex
      - run: make test
      - run: make e2e
```

### Rollback strategy

- **CL parser / Clojure transformer**: deploy as immutable container tags. Rollback = repoint the service to the previous tag. Sub-minute.
- **BaseX schema / XQuery changes**: trickier. Queries are versioned alongside the data; a query that depends on a new index can't run until the index is built. Deploy ordering:
  1. Build new index (online, BaseX supports this).
  2. Deploy new query files.
  3. Deploy consumers of new query results.
- **EDI parser DSL changes** (e.g. adding REF segment support): backward compatible by construction — the parser adds knowledge, doesn't subtract. The Clojure transformer might rely on the new segment; deploy parser first, transformer second.

### Versioning

- Each project gets a semver. Bump on any wire-format change (the EDN, the JSON, the XML).
- Tag releases with `v01-0.1.0`, `v02-0.2.0` (per-project) — git tags scoped by project allow independent release cadence.

---

## Capacity and scaling

Real EDI workloads have specific shapes worth thinking about:

| Workload | Implication |
|---|---|
| **End-of-month claim batches**: 100× normal volume for 6–12 hours | Queue depth-based autoscaling on transformers; parser should NOT autoscale on queue depth — its work is per-file, not per-claim |
| **Eligibility queries**: latency-sensitive, sync | Different topology — a read-path service hitting BaseX directly, not the batch pipeline |
| **Reprocessing**: replay last 30 days through new validation rules | The pipeline must be idempotent. JSON intermediate files in the object store make replay cheap (skip parsing) |
| **Bad-batch quarantine** | A failure quarantine queue is mandatory. A clearinghouse file that fails parsing must not block the rest of the day's batches |

### Idempotency

Every stage MUST be idempotent on `(interchange-control-number, transaction-set-control-number)`. BaseX `ADD` is path-idempotent if the doc path is derived from those numbers (e.g. `claim-{interchange}-{tx}.xml`). The transformer's queue output should be keyed the same way.

Without this, a queue retry creates duplicate claims in BaseX, and adjudication pays the bill twice.

---

## Compliance (the part you can't skip)

EDI 837/835/270/271 carries PHI. Productionizing means:

| Concern | Practical control |
|---|---|
| Encryption in transit | TLS 1.3 minimum; SFTP for ingest; mTLS on internal queues |
| Encryption at rest | Customer-managed keys on object store + datastore; backups encrypted |
| Access logging | Every read of a claim doc logs `(actor, claim_id, timestamp, purpose)` — searchable for 6+ years |
| Minimum necessary | Components see only the fields they need. The analytics sink should NOT receive subscriber names if it's only counting claims |
| BAA chain | Every vendor that touches this data has a signed Business Associate Agreement. Documented. |
| Audit trail | Append-only log of every transaction set received, parsed, transformed, persisted. Stored separately from the data itself, retained 6+ years |
| Breach detection | Anomaly detection on read patterns (sudden bulk read = page on-call) |
| Right-to-access | Provider can request all claims for a member; supported by `eligibility-lookup.xqy` |

This list isn't exhaustive (consult your security/compliance team), but it's the minimum a senior engineer should be able to articulate without notes.

---

## Failure modes in production (vs. the local pipeline)

| Local | Production |
|---|---|
| Parser exits non-zero on bad EDI | Bad EDI goes to quarantine queue; alert fires; rest of batch continues |
| Test fails | Deploy blocked at CI gate; not a runtime concern |
| BaseX query returns empty | Could mean no data, could mean schema mismatch — production needs a synthetic-canary doc to distinguish |
| Pipe-style shell composition | Replaced by message queue at each seam; backpressure is the queue's job |
| Crash = lost work | Stages must checkpoint at the queue boundary; in-flight messages re-deliver |

---

## Cost shape

Order-of-magnitude only:

- **Compute**: dominated by JVM warmup on the transformer if instances are short-lived. Recommendation: long-lived instances, scaled by queue depth, not request rate.
- **Storage**: ~5–50KB per claim XML. A million claims = 50GB. Negligible vs. compute.
- **MarkLogic license** (if used vs. BaseX): six figures annually for a real cluster. Major architectural decision.
- **Queue traffic**: SQS-class pricing. Not a meaningful line item.

---

## What's NOT in this design

- **In-line ML model scoring**. If fraud-detection or auto-adjudication ML enters the picture, it goes in a separate service that consumes the JSON intermediate. Don't add it to the transformer.
- **A real-time eligibility API as the only path**. EDI 270/271 is batch by tradition; a real-time JSON API is a separate concern with different SLAs.
- **Multi-region active-active**. EDI workflows are timezone-bounded by clearinghouse business hours; active-passive is sufficient.

---

## See also

- [architecture.md](architecture.md) — system design that this deployment plan operationalizes
- [monitoring.md](monitoring.md) — observability story for the deployed system
- [onboarding.md](onboarding.md) — local development
