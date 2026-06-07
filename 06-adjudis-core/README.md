# 06 — Adjudis core

A claim adjudication and scrubbing engine. Ingests a normalized claim (project 04's output), runs it through a versioned rule catalog via Clara (forward-chaining inference), and returns a decision with full rule-citation provenance.

This is **MVP / Phase 1** of the platform described in [`../docs/adjudis-plan.md`](../docs/adjudis-plan.md). Phase 2 and 3 are documented there; this directory implements only Phase 1.

## Status

Green on Clojure 1.12.5 + Clara 0.24.0 + Java 24. 13 tests, 36 assertions.

## What's here

```
06-adjudis-core/
├── deps.edn
├── src/adjudis/
│   ├── schema.clj      # shape docs + severity ordering + version constants
│   ├── facts.clj       # Clara fact records: Claim/ServiceLine/Member/HistoricalLine/CatalogRule/Finding
│   ├── dates.clj       # ISO/CCYYMMDD parsing, age-on, benefit-year helpers
│   ├── catalog.clj     # load rule library from resources/rule-catalog/*.edn
│   ├── rules.clj       # Clara productions (one per rule category)
│   ├── history.clj     # claim-history store (atom; XTDB in Phase 2)
│   ├── engine.clj      # public adjudicate API
│   ├── explain.clj     # findings → decision shape
│   └── cli.clj         # stdin JSON → stdout JSON
├── resources/
│   ├── rule-catalog/   # the rule library (6 categories, 11 rules)
│   │   ├── frequency.edn
│   │   ├── age-appropriate.edn
│   │   ├── annual-max.edn
│   │   ├── fee-schedule.edn
│   │   ├── pre-auth.edn
│   │   └── eligibility.edn
│   └── fixtures/       # synthetic members, history, claims
└── test/adjudis/
    └── engine_test.clj  # one test per rule class + integration
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

### CLI usage

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

The output is one decision JSON per claim, with the full citation chain (every fired rule, with reason code and source).

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

## What it does NOT do (MVP scope)

- **Real CMS rule sources.** The rule library is synthetic, hand-curated. Phase 2 ingests NCCI/MUE from CMS quarterly publications.
- **Bitemporal storage.** History is a flat list keyed by subscriber. Phase 2 puts it in XTDB and supports "what was this member's adjudicated spend as of June 1?"
- **Real eligibility (270/271).** Member coverage is a plain EDN record. Phase 2 puts a 270/271 adapter behind the eligibility check.
- **Multi-tenant data isolation.** Single global catalog. Phase 3 adds per-tenant overlays.
- **HTTP API + auth.** CLI only. Phase 3 puts a Reitit API in front.
- **Performance benchmarking.** No throughput target. Phase 2 measures p99 against a 500-rule catalog.

## Acceptance criteria

- [x] Six rule categories implemented (frequency, age, pre-auth, eligibility, annual-max, fee-schedule)
- [x] Catalog format = pure data (EDN)
- [x] Full rule-citation chain on every decision (rule-id, reason-code, citation source)
- [x] Decision shape conforms to documented schema
- [x] Test per rule class + integration tests
- [x] CLI consumes project 04's normalized-claim JSON
- [x] History store with XTDB-swappable protocol
- [x] Verified green on Clojure 1.12.5 + Clara 0.24.0

## See also

- [`../docs/adjudis-plan.md`](../docs/adjudis-plan.md) — full 3-month development plan, Phase 2 and 3 scope
- [`../docs/architecture.md`](../docs/architecture.md) — system view (Adjudis sits above the existing pipeline)
- [`../04-clojure-edi-transform/`](../04-clojure-edi-transform/) — upstream stage
