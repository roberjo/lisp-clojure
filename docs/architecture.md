# Architecture

A technical deep dive into how the six projects fit together. Companion to per-project READMEs; assume you've read the top-level `README.md` and skimmed each project's README.

The original five projects form a unix-style data pipeline (EDI → EDN → JSON → XML). Project 06 sits parallel to that pipeline as a SaaS-shaped adjudication engine consuming the same JSON contract.

---

## System view

```
┌──────────────┐
│  X12 EDI     │     wire format from clearinghouses (synthetic .edi here)
│  (837D, ...) │
└──────┬───────┘
       │ parse-file
       ▼
┌─────────────────────────────────────────────┐
│ Project 02 — x12-parser  (Common Lisp)      │
│                                             │
│   ┌─────────────┐    ┌───────────────────┐  │
│   │ delimiters  │───▶│  parser           │  │
│   │ (ISA-pos)   │    │  ISA→GS→ST tree   │  │
│   └─────────────┘    └────────┬──────────┘  │
│                               │             │
│                  CLOS instances (per-tx)    │
└──────────────────────┬──────────────────────┘
                       │ uses
                       ▼
┌─────────────────────────────────────────────┐
│ Project 03 — edi-dsl  (Common Lisp)         │
│                                             │
│   define-segment macro  ─▶  *registry*      │
│   transaction CLOS hierarchy                │
│   validate (APPEND method combination)      │
│   serialize / transform-to                  │
└──────────────────────┬──────────────────────┘
                       │ transform-to :plist
                       ▼
              (plist-EDN on stdout)
                       │
                       │ pipe
                       ▼
┌─────────────────────────────────────────────┐
│ Project 04 — edi.transform  (Clojure/JVM)   │
│                                             │
│   stdin EDN ─▶ transducer pipeline          │
│              ─▶ Malli validation            │
│              ─▶ JSON on stdout              │
└──────────────────────┬──────────────────────┘
                       │ pipe
                       ▼
                  (JSON Lines)
                       │
                       │ python from-json.py --multi
                       ▼
                 (XML documents)
                       │
                       │ basex ADD
                       ▼
┌─────────────────────────────────────────────┐
│ Project 05 — docstore  (BaseX / XQuery)     │
│                                             │
│   per-transaction XML documents             │
│   indexes (range / text / attribute)        │
│   eligibility / claim-status / aggregate    │
└─────────────────────────────────────────────┘
```

Project 01 (`kvstore`) is intentionally outside this flow. It exists to demonstrate the CL toolchain shape (ASDF, packages, generics, FiveAM) without coupling that demonstration to X12.

Project 06 (`adjudis-core`) is a parallel sink off the JSON intermediate: instead of (or in addition to) loading into a document store, claims are run through a Clara-based rules engine that emits an adjudication decision with full rule-citation provenance. It has grown well beyond the MVP described in [adjudis-plan.md](adjudis-plan.md) — Phase 1 (engine), Phase 2 (versioning, shadow-mode, author CLI, benchmark), and most of Phase 3 (HTTP API, container, CI, multi-tenant overlays, API-key auth, structured logging, audit log, Prometheus metrics) have shipped.

```
                  04 JSON output
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
       05 XML/BaseX          06 adjudis-core
       (retrieval)           (decision + citations)
                             │
                             ├── CLI         (stdin JSON → stdout decision JSON)
                             ├── Author CLI  (validate, dry-run, diff, shadow)
                             ├── HTTP API    (Reitit + ring-jetty, packaged as
                             │                a multi-stage Dockerfile)
                             │   ├── auth    (X-API-Key → tenant)
                             │   ├── tenants (per-tenant catalog overlay)
                             │   └── obs     (JSON logs, audit log,
                             │                X-Request-Id, /metrics)
                             └── Build       (tools.build uberjar)
```

---

## Interface contracts

The cross-language seams matter more than any single component. The repo gets away with very small components precisely because each seam is rigorously typed in some sense.

### Seam 1: project 02 → project 03

In-process. Project 02 calls into `edi-dsl`:

- `(define-segment "ID" (:name :sym) ...elements...)` — declarative, evaluated at compile time, populates `*segment-registry*`.
- `(make-instance 'dental-claim-transaction :control-number "0001" :segments ...)` — direct CLOS instantiation.
- `(validate tx)` — generic with `append` method combination. New mixins on the transaction add validation without coordinating.

The contract is the CLOS hierarchy and the segment-registry hash table. No serialization here.

### Seam 2: project 02 → project 04 (CL → Clojure)

Serialized through EDN. The CL emitter is [`02-x12-parser/bin/emit-plist.lisp`](../02-x12-parser/bin/emit-plist.lisp), which calls `(transform-to tx :plist)` and `prin1`s with two non-default bindings that MUST be in place or the consumer breaks:

| Binding | Why |
|---|---|
| `*print-readably* t` | quoted strings and printable structure |
| `*print-case* :downcase` | Clojure EDN keywords are case-sensitive; CL defaults to upcase |

The output is one plist per line. Each plist has shape:

```clojure
(:type :dental-claim-transaction
 :control-number "0001"
 :segments
  ((:id "ST"  :elements ("837" "0001" "005010X224A2"))
   (:id "BHT" :elements ("0019" "00" "REF12345" "20240601" "1200" "CH"))
   ...))
```

The Clojure side validates this with `malli` ([`schema.clj/Transaction`](../04-clojure-edi-transform/src/edi/transform/schema.clj)). If the schema fails, the bug is on the CL emit side — fail loudly.

### Seam 3: project 04 → project 05 (Clojure → Python → XML)

Two-stage: Clojure emits one JSON object per line (JSONL), Python's `from-json.py --multi` reads that stream and produces pretty-printed XML per claim.

Why a Python stage at all? The Clojure side could emit XML directly, but:

- XML serialization in Clojure ecosystems is fine but not first-class; `data.xml` is fine for output but the namespace ceremony is noisy.
- Python's `xml.etree` is in the standard library, and a 90-line script with no deps is easier for an ops team to inspect/modify than a Clojure JAR.
- This makes the JSON intermediate a real artifact — debuggable, replay-able, ingest-able by other tooling.

The JSON schema is [`NormalizedClaim`](../04-clojure-edi-transform/src/edi/transform/schema.clj) in Malli; if Python emits an XML for a JSON that doesn't conform, it's the JSON producer's bug.

### Seam 4: XML → BaseX

BaseX `ADD` is idempotent on path. The XML must match the schema namespace `urn:x12:837d` for the XQueries to find it (every XQuery declares `c = "urn:x12:837d"` and queries through that prefix).

### Seam 5: project 04 JSON → project 06 (adjudication)

The same JSON shape that 05 turns into an XML document, project 06 consumes as an input to adjudication. Two consumption paths:

- **CLI**: stdin JSONL → stdout decision JSONL. The shape is documented in [`06-adjudis-core/src/adjudis/schema.clj`](../06-adjudis-core/src/adjudis/schema.clj).
- **HTTP** (`POST /adjudicate`): `{"claim": {...}, "member": {...}, "as-of": "YYYY-MM-DD"?}`. The `claim` field is the project-04 JSON shape; the `member` field is a small EDN-like map with subscriber id, DOB, and coverage dates.

The decision shape — `{verdict, line-decisions, findings, rule-versions, ...}` — is also documented in `schema.clj`. Every fired rule's id, reason code, severity, and citation are in `:findings`, so the decision is reconstructable from `(claim + catalog + findings)`.

### Seam 6: project 06 → external observers

Three sinks for runtime data:

- **stdout** — one structured JSON log line per event. Two streams: application logs and the `audit` logger. MDC propagates `request_id` and `tenant_id` onto every line.
- **`/metrics`** — Prometheus exposition format. JVM defaults + HTTP latency histograms + adjudis-specific counters (auth attempts by outcome, adjudications by tenant+verdict, findings by category+severity).
- **`X-Request-Id` response header** — honored if the client supplied one, otherwise generated; same id used as `request_id` in the logs.

---

## Why two languages plus a doc store

The job description names Lisp, Clojure, and MarkLogic as the working stack. The portfolio mirrors that structure on purpose. But the architectural argument for each is real:

- **Common Lisp for parsing.** X12 is a tagged hierarchical format; CLOS handles transaction-set polymorphism naturally (the same `validate` call dispatches to dental vs. professional vs. remittance specifics). Macros let the schema live in declarations, not in `if`-chains scattered through a parser.
- **Clojure for transformation.** Once the document is in memory, the work is bulk re-shaping: pick segments, flatten loops, map element positions to named keys, group service lines. That's exactly what transducers and `clojure.spec`/Malli are good at.
- **Document store for retrieval.** A claim is naturally one nested document. Joining six tables to assemble a claim view for adjudication is busywork that a document store skips. XQuery operates on the document tree directly.

A single-language design (everything in CL, or everything in Clojure) would lose the demonstration value AND skip the genuine ergonomic wins each language offers for its phase.

---

## Key design decisions

### Internal representation: CLOS objects holding plists

The parsed-segment list inside each transaction object is a list of plists like `("CLM" "PCN001" "250.00" ...)`. The transaction object itself is a CLOS instance.

**Why not all-CLOS (every segment becomes a class)?** Cost — each segment would need its own class, every element a slot. Adding a segment becomes a structural change. With plists, adding a segment is one `define-segment` declaration; the parser doesn't change.

**Why not all-plist (no CLOS at all)?** Loses dispatch. `(validate tx)` works because CLOS picks the right combination of methods for `dental-claim-transaction` vs. `remittance-transaction`. A plist-only design ends up with a `case` on `(:type tx)` somewhere — that's a CLOS class hierarchy with extra steps.

The hybrid: CLOS for cross-segment behavior, plists for per-segment data. Trade-off: element access is positional (`(nth 2 seg)`) which is fragile to spec changes. Acceptable because X12 element ORDER is part of the spec; reordering would already be a breaking change.

### `append` method combination on `validate`

`validate` returns a list of errors, never signals. Method combination is `append`, which means every applicable method's return value is concatenated.

Concrete benefit: the `auditable` mixin contributes a "missing transaction control number" check. `dental-claim-transaction` contributes a "must have at least one CLM" check. Neither knows about the other. Adding a new mixin (`phi-tagged`, `medicaid-state-rules`) is a `defmethod`, not a refactor.

Alternative considered: a chain of `:before/:after` methods on a primary `validate` that accumulates into a thread-local. That works but threads global state through the call. `append` combination is the idiomatic CLOS expression.

### Errors as data, not signaled conditions

`validation-error` is a `defstruct` with `segment-id`, `element-position`, `loop`, `message`. Collected into a list, returned. The caller can render however it wants.

The X12 standard's error model is "report all the bad news for one document at once" — a clearinghouse rejecting a claim should tell the submitter every problem, not the first one. A condition-and-signal design fundamentally fights that.

`parse-error` (now `x12-parse-error` due to the package-lock collision) IS a condition because it's structural — at that point the document isn't a document anymore, there's nothing further to validate.

### Two-pass parse, not streaming

The parser reads the whole interchange, splits into a flat segment list, then walks the list assembling the envelope tree. Memory cost is O(file). For real-world batch 837 files (multi-GB) this is wrong — needs to be a SAX-style segment-at-a-time emitter.

Flagged as v2 in [`02-x12-parser/README.md`](../02-x12-parser/README.md). The refactor would replace `parse-string` with a generator-style API; the assembly walker stays the same.

### Transducers, not threading macros

Project 04's `all-service-lines` is `(comp (filter ...) (map-indexed ...))` not `(->> segs (filter ...) (map-indexed ...))`.

Wins: the same xform works over an in-memory vector AND a `core.async` channel, with zero changes to the xform. If project 02 eventually streams, project 04 doesn't need any rewrite — `(into [] xform ch)` becomes `(a/transduce xform ...)`.

### Per-transaction document granularity in BaseX

One XML document = one ST/SE transaction. Alternatives considered:

| Granularity | Why rejected |
|---|---|
| Per-interchange (ISA/IEA) | A single eligibility request would force an entire batch to be rewritten on every update |
| Per-line-item (SV3) | Loses the claim context that adjudication needs |

Per-transaction matches both the natural unit of adjudication and the natural unit of update.

### Rule productions are code; rule instances are data (project 06)

The Clara productions in [`06-adjudis-core/src/adjudis/rules.clj`](../06-adjudis-core/src/adjudis/rules.clj) define **categories** of rule (frequency limit, age-appropriate, pre-auth, eligibility, annual max, fee schedule). The specific rules ("D1110 limited to 2/year") live in EDN data files under `resources/rule-catalog/`. Adding a specific rule is a single map appended to a file; adding a category is a new production.

This is the right shape because rule authors in production are clinical-admin staff, not engineers. The data path is what they edit; the code path is owned by engineering. The compose layer ([`tenants/apply-overlay`](../06-adjudis-core/src/adjudis/tenants.clj)) lets each tenant `:add`, `:override`, or `:remove` rules from the shipped base catalog without touching engineering code at all.

### Tenant isolation by data flow, not trust (project 06)

The Clara engine doesn't know about tenants. The HTTP handler computes the requesting tenant's effective catalog (`base ⊕ overlay`) and passes it as an argument to the engine. No shared mutable state. A regression that conflates two tenants' findings would show up in [`api_test/tenant-isolation-acme-rule-doesnt-leak-to-beta`](../06-adjudis-core/test/adjudis/api_test.clj).

---

## Module-level dependencies

```
                       ┌──────────────────┐
                       │   project 01     │  (independent)
                       │   kvstore        │
                       └──────────────────┘

                       ┌──────────────────┐
                       │   project 03     │  declares ASDF
                       │   edi-dsl        │  exports: define-segment, CLOS hierarchy,
                       │                  │           validate, transform-to
                       └────────┬─────────┘
                                │ asdf depends-on
                                ▼
                       ┌──────────────────┐
                       │   project 02     │  consumes 03's primitives
                       │   x12-parser     │  emits plist-EDN on stdout
                       └────────┬─────────┘
                                │ pipe (process boundary)
                                ▼
                       ┌──────────────────┐
                       │   project 04     │  reads plist-EDN
                       │   edi.transform  │  emits JSON on stdout
                       └────────┬─────────┘
                                │ pipe (process boundary)
                                ▼
                                │
                          ┌─────┴──────┐
                          ▼            ▼
                       ┌──────────────────┐  ┌──────────────────┐
                       │   project 05     │  │   project 06     │  parallel sink
                       │   docstore       │  │   adjudis-core   │  consumes 04's
                       │                  │  │                  │  JSON shape
                       │ Python from-json │  │ Clara engine     │
                       │ BaseX ADD / XQ   │  │ HTTP API         │
                       └──────────────────┘  │ Multi-tenant     │
                                             │ Observability    │
                                             └──────────────────┘
```

ASDF dependencies cross only inside the CL boundary (02 → 03). Beyond that the components communicate by IPC over text streams (or HTTP, in project 06's case) — small, debuggable interfaces. You can run any stage in isolation, with a saved fixture as input.

---

## Failure modes

Where each component falls over and how it tells you about it.

| Stage | Failure | How it's surfaced |
|---|---|---|
| 02 parse | Non-ISA input, length < 106 | `x12-parse-error` with offset 0 |
| 02 parse | Segment outside ST/SE | `x12-parse-error` |
| 02 validate | Required element missing | `validation-error` in list (segment id, element position) |
| 02 validate | Missing CLM on dental claim | `validation-error` with loop "2300" |
| 03 dsl | Unknown segment | `validation-error` "Unknown segment id" |
| 04 transform | Malli schema fails | Logged to stderr, JSON still emitted (deliberately permissive — downstream can quarantine) |
| 04 transform | Bad EDN on stdin | Clojure `clojure.edn` error, exits non-zero |
| 05 BaseX | Missing namespace prefix in XML | Query returns empty — silent. The loader script should validate before `ADD`. |
| 06 HTTP API | Missing/bad `X-API-Key` (multi-tenant mode) | 401 with `{"error":"unauthorized"}`; `auth_failed` audit-log entry |
| 06 HTTP API | Missing required body field on `/adjudicate` | 400 with `{"error":"validation","details":{"missing":[...]}}` |
| 06 HTTP API | Unknown route | 404 (Reitit default) |
| 06 engine | Unknown rule category | Catalog rule passes schema check at load (categories enumerated in `author.clj`); a previously-unseen category is caught by the rule-author CLI's `validate` |
| 06 engine | Catalog entry malformed | `(author/validate-catalog)` enumerates missing keys, unknown categories/severities, duplicate ids before deploy |

The most insidious failure (other than the BaseX namespace silence) is a rule that doesn't fire when the author expected it to. The shadow-mode CLI catches this: run the claim through the proposed catalog AND the current catalog and inspect the delta before promoting.

---

## What's deliberately NOT here

- **No 837P, 837I, 835, 270/271, 276/277 in the parser.** Scope limit. Adding 835 (remittance) is documented in [`02-x12-parser/README.md`](../02-x12-parser/README.md) as a stretch.
- **No streaming parse in project 02.** v2.
- **No auth in the data pipeline (projects 02 → 05).** The pipeline is a unix-style filter — auth there would live at the ingest boundary (clearinghouse SFTP, BaseX REST). Project 06's HTTP API does have auth (X-API-Key); it's a different shape of component.
- **No retry / dead-letter queue in the pipeline.** The pipeline is push-from-stdin, push-to-stdout; retry is the caller's problem. Productionizing this design would put the queue at the seams (see [deployment.md](deployment.md)).
- **No PHI scrubbing utilities.** Fixtures are synthetic and obviously fake. Real claim data must never touch this repo — the `.gitignore` enforces this with `samples/*.edi`. The logging story in project 06 explicitly excludes PHI from log payloads (only ids and structural facts).
- **No RBAC inside a tenant (project 06).** API keys identify tenants, not users. An analyst-vs-admin distinction inside one tenant is Phase 3.B in [`adjudis-plan.md`](adjudis-plan.md).
- **No XTDB-backed bitemporal storage yet.** The history store uses an atom; the protocol is XTDB-swappable when ready.
- **No OpenTelemetry traces.** Sketched in [`monitoring.md`](monitoring.md); structured logs + correlation IDs are the current substitute.

---

## See also

- [onboarding.md](onboarding.md) — where to start reading the code
- [local-dev-setup.md](local-dev-setup.md) — install steps for each OS
- [deployment.md](deployment.md) — operational design notes
- [monitoring.md](monitoring.md) — observability story
- [x12-primer.md](x12-primer.md) — domain background
