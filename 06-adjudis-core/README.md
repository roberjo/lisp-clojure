# 06 — Adjudis core

A claim adjudication and scrubbing engine. Ingests a normalized claim (project 04's output), runs it through a versioned rule catalog via Clara (forward-chaining inference), and returns a decision with full rule-citation provenance.

This is **MVP / Phase 1** of the platform described in [`../docs/adjudis-plan.md`](../docs/adjudis-plan.md). Phase 2 and 3 are documented there; this directory implements only Phase 1.

## Status

Green on Clojure 1.12.5 + Clara 0.24.0 + Java 24. **30 tests, 80 assertions.**

Phase 1 (MVP) shipped. Phase 2 features added incrementally:
- ✅ Rule versioning (effective-from / effective-to + as-of replay)
- ✅ Shadow-mode adjudication (current vs proposed catalog with delta)
- ✅ Rule-author CLI (validate, dry-run, diff, shadow)
- ✅ Synthetic catalog generator + latency benchmark
- ⏳ XTDB integration (still atom-based store)
- ⏳ Real CMS NCCI / MUE ingestion
- ⏳ HTTP API + multi-tenancy (Phase 3)

**Benchmark snapshot** (`make bench-06`):

| Catalog size | Mean | p50 | p95 | p99 |
|---:|---:|---:|---:|---:|
| 11 (shipped) | 23 ms | 17 ms | 62 ms | 92 ms |
| 50 (synthetic) | 22 ms | 14 ms | 65 ms | 135 ms |
| 200 (synthetic) | 15 ms | 12 ms | 33 ms | 48 ms |
| 500 (synthetic) | 29 ms | 21 ms | 67 ms | **83 ms** |

The Phase 2 SLO target — sub-100ms p99 on a 500-rule catalog — is hit. Clara's Rete-style network keeps the cost sub-linear in rule count because the alpha network prunes early.

## What's here

```
06-adjudis-core/
├── deps.edn               # aliases: :test :run-cli :author :bench
├── src/adjudis/
│   ├── schema.clj          # shape docs + severity ordering + version constants
│   ├── facts.clj           # Clara fact records (Claim/ServiceLine/Member/...)
│   ├── dates.clj           # ISO/CCYYMMDD parsing, age-on, benefit-year helpers
│   ├── catalog.clj         # load rule library from resources/rule-catalog/*.edn
│   ├── versions.clj        # rule-active-on?, active-rules, diff-catalogs
│   ├── rules.clj           # Clara productions (one per rule category)
│   ├── history.clj         # claim-history store (atom; XTDB in Phase 2)
│   ├── engine.clj          # public adjudicate API (with as-of replay)
│   ├── shadow.clj          # current-vs-proposed adjudication + batch summary
│   ├── explain.clj         # findings → decision shape
│   ├── cli.clj             # stdin JSON → stdout JSON
│   └── author.clj          # rule-author CLI: validate / dry-run / diff / shadow
├── resources/
│   ├── rule-catalog/       # the rule library (6 categories, 11 rules)
│   └── fixtures/           # synthetic members, history, claims
└── test/adjudis/
    ├── engine_test.clj
    ├── engine_versioned_test.clj   # rule versioning + as-of replay
    ├── shadow_test.clj             # shadow-mode adjudication
    ├── versions_test.clj           # version filtering + catalog diffing
    ├── author_test.clj             # author-CLI validation
    ├── catalog_synth.clj           # procedural large-catalog generator
    └── bench.clj                   # latency benchmark harness
```

## The architectural punchline

**Rule productions are code; rule instances are data.**

- `rules.clj` has ONE Clara `defrule` per rule category (frequency, age, fee schedule, pre-auth, eligibility, annual max). Six productions, fixed.
- `resources/rule-catalog/*.edn` is the rule library. Adding "limit D1208 to 4/year" is a single map appended to `frequency.edn`. No code change. No engine restart (in Phase 2 with hot reload). No test change beyond a new fixture.
- New rule **categories** are a new `defrule` + a new catalog file. The architecture absorbs the addition without ceremony.

This is the right shape because in production, rule authors are clinical-admin staff and reimbursement analysts, not engineers. The catalog must be editable by them; the productions are owned by the engineering team.

## Running locally

Requires the Clojure CLI on PATH. From this directory:

```bash
clojure -X:test
```

End-to-end with the rest of the repo (from the repo root):

```bash
# tests across all 5 working projects:
make test

# pipeline: EDI -> EDN -> JSON -> XML -> XQuery
make e2e

# pipeline + adjudicate the resulting claim:
make adjudicate-demo
```

### Adjudication CLI (`:run-cli`)

```bash
# Single claim:
cat resources/fixtures/claim-clean.json \
  | clojure -M:run-cli --member resources/fixtures/member-doe-jane.edn

# With history (so frequency / annual-max rules see prior visits):
cat resources/fixtures/claim-third-prophy.json \
  | clojure -M:run-cli \
      --member  resources/fixtures/member-doe-jane.edn \
      --history resources/fixtures/history-doe-jane.edn

# Batch (JSON Lines):
cat batch.jsonl | clojure -M:run-cli --member member.edn
```

### Rule-author CLI (`:author`)

For non-engineers who maintain the catalog:

```bash
# Schema-check the shipped catalog:
clojure -M:author validate

# Schema-check a proposed-changes directory:
clojure -M:author validate --catalog /path/to/proposed-catalog/

# Dry-run a claim against a catalog:
clojure -M:author dry-run \
  --claim  resources/fixtures/claim-clean.json \
  --member resources/fixtures/member-doe-jane.edn

# Diff two catalog directories:
clojure -M:author diff --before catalog-v1/ --after catalog-v2/

# Shadow-mode: current vs proposed on one claim:
clojure -M:author shadow \
  --claim    resources/fixtures/claim-clean.json \
  --member   resources/fixtures/member-doe-jane.edn \
  --proposed /path/to/proposed-catalog/
```

### Benchmark (`:bench`)

```bash
clojure -M:bench
# Reports mean / p50 / p95 / p99 across 4 catalog sizes.
```

## Sample decision

Input (the third-prophy fixture):

```json
{"claim-id":"PCN999",
 "subscriber":{"member-id":"M00112233"},
 "service-lines":[{"line-number":1,"procedure-code":"D1110","charge":150.00,"service-date":"2024-12-01","units":1}]}
```

Output (abridged):

```json
{"claim-id":"PCN999",
 "verdict":"denied",
 "total-charged":150.0,
 "total-allowed":0.0,
 "line-decisions":[
   {"line-number":1,"verdict":"denied","allowed":0.0,
    "reason-codes":["FREQ-PROPHY-ADULT","FEE-ADJ"]}],
 "findings":[
   {"rule-id":"DENTAL-PROPHY-ADULT-FREQ","rule-category":"frequency-limit",
    "severity":"deny","reason-code":"FREQ-PROPHY-ADULT",
    "message":"Adult prophylaxis (D1110) limited to 2 per benefit year. — observed 3 occurrences (limit: 2)",
    "affected-lines":[1],
    "citation":{"source":"ADA CDT 2024; standard plan benefit limit"},
    "extra-context":{"observed-count":3,"limit":2}},
   {"rule-id":"DENTAL-FEE-SCHEDULE-INN","rule-category":"fee-schedule",
    "severity":"adjust","reason-code":"FEE-ADJ",
    "message":"Line 1 charge $150.00 exceeds scheduled allowed $100.00 (adjustment: $50.00)",
    "affected-lines":[1],
    "citation":{"source":"Sample in-network fee schedule (synthetic)"},
    "extra-context":{"charged":150.0,"allowed":100.0,"adjustment":50.0}}],
 "rule-versions":{"catalog-version":"2024-Q2-mvp","engine-version":"0.1.0"}}
```

Every fired rule is cited. The decision is reconstructable from `(claim + catalog version + findings)` — the audit chain is in the data, not in side-channel logs.

## Rule categories shipped

| Category | Productions | Example rule |
|---|---|---|
| `:frequency-limit` | 1 | "D1110 limited to 2/year" |
| `:age-appropriate` | 2 (min and max age) | "D1120 is for patients under 14" |
| `:pre-auth-required` | 1 | "Crowns over $500 need pre-auth" |
| `:eligibility` | 1 | "Member must be active on DOS" |
| `:annual-maximum` | 1 | "$1,500 cap per benefit year" |
| `:fee-schedule` | 1 | "In-network allowed amounts" |

Each is implemented as a single Clara production reading catalog facts and emitting `Finding` records.

## Design decisions

### Records, not maps, for facts

Clara dispatches productions on fact type. With records the type is the class (free); with maps you'd need an explicit `:type` field on every fact and a custom `:fact-type-fn`. Records are the right cost-benefit choice.

### Atom-based history store with a protocol

The `HistoryStore` protocol has two methods — `lookup-lines` and `record-decision!`. The atom-based `AtomStore` is the MVP implementation. Phase 2 will write `XtdbStore` against the same protocol and the engine doesn't change.

### Severity ordering, not verdict ordering

The decision logic combines findings by max severity (`:deny > :pending > :adjust > :warn > :inform`). A single deny outranks ten adjusts. The verdict mapping is then trivial. This is a more compositional model than "decide line verdict from a rule directly" — it lets rules contribute findings independently, and the explain layer resolves.

### EDN catalog, not YAML/JSON

Catalog files are Clojure data. Sets (`#{"D1110"}`) are first-class without conversion. Maps preserve key types (`:max-age 13` vs `"max-age": 13`). Round-trips through `pr-str` / `edn/read` perfectly. Rule authors edit them in the same editor they edit code in.

### No web UI yet

MVP is CLI-only. Phase 3 adds the HTTP API + web rule-author UI. Doing it now would mean carrying ~10 more deps for no MVP benefit.

## What it does NOT do yet

- **Real CMS rule sources.** The rule library is synthetic, hand-curated; the synthetic generator stresses the engine but isn't real NCCI/MUE. Phase 2 continuation work.
- **Bitemporal storage.** History is a flat list keyed by subscriber. Versioning + replay work via `as-of` in the engine, but full bitemporal queries ("what was the member's adjudicated spend as of June 1, with this catalog version?") need XTDB.
- **Real eligibility (270/271).** Member coverage is a plain EDN record. Phase 2 will put a 270/271 adapter behind the eligibility check.
- **Multi-tenant data isolation.** Single global catalog. Phase 3 adds per-tenant overlays.
- **HTTP API + auth.** CLI only. Phase 3 puts a Reitit API in front.

## Acceptance criteria

**Phase 1 (MVP):**
- [x] Six rule categories implemented (frequency, age, pre-auth, eligibility, annual-max, fee-schedule)
- [x] Catalog format = pure data (EDN)
- [x] Full rule-citation chain on every decision (rule-id, reason-code, citation source)
- [x] Decision shape conforms to documented schema
- [x] Test per rule class + integration tests
- [x] CLI consumes project 04's normalized-claim JSON
- [x] History store with XTDB-swappable protocol
- [x] Verified green on Clojure 1.12.5 + Clara 0.24.0

**Phase 2 (shipped this iteration):**
- [x] Rule versioning with `:effective-from` / `:effective-to`
- [x] `as-of` replay support (re-adjudicate historical claims under historical catalog)
- [x] Shadow-mode adjudication (current vs proposed catalog with delta)
- [x] Batch shadow summary (verdict-change distribution + dollar impact)
- [x] Rule-author CLI: validate / dry-run / diff / shadow
- [x] Catalog schema validation (required keys, known categories, severities, duplicate ids)
- [x] Procedural large-catalog generator + latency benchmark
- [x] Hit Phase 2 SLO: sub-100ms p99 on a 500-rule catalog (83ms)

## See also

- [`../docs/adjudis-plan.md`](../docs/adjudis-plan.md) — full 3-month development plan, Phase 2 and 3 scope
- [`../docs/architecture.md`](../docs/architecture.md) — system view (Adjudis sits above the existing pipeline)
- [`../04-clojure-edi-transform/`](../04-clojure-edi-transform/) — upstream stage
