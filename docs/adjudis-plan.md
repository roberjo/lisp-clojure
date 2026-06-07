# Adjudis — development plan

A multi-tenant claim adjudication and scrubbing platform. This document is the build plan; the implementation skeleton lives in `06-adjudis-core/`.

> **Scope honesty.** The full product is a 6–18 month build for a serious team. This plan defines the 3-month MVP that's a credible portfolio piece, with a path to the full product. What's actually shipped today (the MVP) is called out per-phase.

---

## Product summary

> A SaaS platform that ingests an X12 837 claim, runs it through a versioned rule set (NCCI + MUE + LCD + payer-specific + custom client rules), returns an adjudication decision with full rule-citation provenance, and feeds analytics on rule effectiveness and denial trends.

Adjacent products: ClaimsXten (Change Healthcare), Cotiviti edits, Optum's claim editor. Market: $billions; every payer and clearinghouse buys this category.

---

## Why Lisp/Clojure for this

1. **Rule engines are Lisp's home turf.** Forward-chaining inference, pattern matching, declarative authoring — Clara (Clojure, Rete-based) is production-grade. Hand-rolled Lisp forward chainers are a 50-year tradition.
2. **Explanation graphs are graph-shaped.** Persistent data structures + content-addressed audit logs play to the language strengths.
3. **DSL authoring matters.** Rule authors are clinical-admin staff, not engineers. Lisp macros and data-as-code let the rule library be authored, version-controlled, and reviewed without ceremony.
4. **Bitemporal storage** via XTDB is mature in Clojure and answers questions ("what would this claim's adjudication have been on June 1?") that are mandatory for retroactive rule changes — a constant requirement in real payer workflows.

---

## Architecture

```
                  Existing repo (this codebase)
                  02 parser → 03 DSL → 04 transform → JSON claim
                                  │
                                  ▼
   ┌───────────────────────────────────────────────────────────┐
   │  06-adjudis-core                                          │
   │                                                           │
   │  ┌──────────────┐    ┌──────────────────┐                 │
   │  │ Rule catalog │───▶│ Compiler         │──┐              │
   │  │ (EDN data)   │    │ Catalog → Clara  │  │              │
   │  └──────────────┘    │ facts            │  │              │
   │                       └──────────────────┘ │              │
   │                                            ▼              │
   │  ┌─────────────────────────────────────────────────────┐  │
   │  │ Adjudication engine (Clara forward chainer)         │  │
   │  │  productions per rule class:                        │  │
   │  │   • frequency-limit                                 │  │
   │  │   • age-appropriate-code                            │  │
   │  │   • modifier-validation                             │  │
   │  │   • annual-maximum-benefit                          │  │
   │  │   • fee-schedule application                        │  │
   │  │   • eligibility (member active + benefit-covered)   │  │
   │  │   • pre-auth-required                               │  │
   │  └────────────────────┬────────────────────────────────┘  │
   │                       ▼                                   │
   │  ┌─────────────────────────────────────────────────────┐  │
   │  │ Explanation graph                                   │  │
   │  │  every fired rule → cited                           │  │
   │  │  rule-not-fired-but-relevant → optionally captured  │  │
   │  │  decision rationale chain                           │  │
   │  └────────────────────┬────────────────────────────────┘  │
   │                       ▼                                   │
   │  ┌─────────────────────────────────────────────────────┐  │
   │  │ Decision                                            │  │
   │  │ {                                                   │  │
   │  │   verdict: :paid | :denied | :pending               │  │
   │  │   line-decisions: [...]                             │  │
   │  │   patient-responsibility: 42.00                     │  │
   │  │   provider-payment: 158.00                          │  │
   │  │   reason-codes: [CO-45, …]                          │  │
   │  │   findings: [<every rule that fired, with cite>]    │  │
   │  │ }                                                   │  │
   │  └────────────────────┬────────────────────────────────┘  │
   │                       ▼                                   │
   │  ┌─────────────────────────────────────────────────────┐  │
   │  │ Claim history store (MVP: atom; Phase 2: XTDB)      │  │
   │  └─────────────────────────────────────────────────────┘  │
   └───────────────────────────────────────────────────────────┘
```

### Wire format contracts

- **Input:** normalized claim JSON as project 04 emits (see [`04-clojure-edi-transform/src/edi/transform/schema.clj`](../04-clojure-edi-transform/src/edi/transform/schema.clj)).
- **Output:** adjudication decision JSON, schema defined in `06-adjudis-core/src/adjudis/schema.clj`.
- **History:** EDN files on disk (MVP); XTDB transactions (Phase 2).
- **Rule catalog:** EDN files in `06-adjudis-core/resources/rule-catalog/`.

---

## Three-phase plan

### Phase 1 — MVP (ships in this PR)

**Goal:** prove the architecture. A single tenant, a hand-curated rule library, in-memory storage, single-claim CLI.

- [x] Project scaffold (deps.edn, src/test layout)
- [x] Rule catalog format (EDN) and a starter library covering 6 rule classes
- [x] Clara-based engine with one production per rule class
- [x] Explanation graph emitted with every decision
- [x] In-memory claim history store with the API surface XTDB will later implement
- [x] CLI consuming project 04's JSON output
- [x] Synthetic claim-history fixture (member with 3 prior visits, demonstrating frequency rules)
- [x] Test suite covering each rule class + an integration test

**What it proves:** the architecture works. The CLI takes a claim, returns a decision with citations. Adding a new rule class is a single new Clara production + corresponding catalog entries. Adding a new specific rule is a single EDN map.

**What it does NOT prove:** scale (50,000 rules), real eligibility (270/271 exchange), multi-tenant isolation, audit-grade storage, the SaaS shell.

### Phase 2 — Depth (weeks 4–8)

- [ ] Ingest real CMS rule sources
  - NCCI procedure-to-procedure (PTP) edits from CMS quarterly
  - MUE (Medically Unlikely Edits) values
  - Get to 500+ real rules in the catalog
- [ ] XTDB integration (replace in-memory store)
  - Bitemporal: "what would this claim's adjudication have been on date X?"
  - Audit log: every rule edit by every user
- [x] Rule versioning (effective-from / effective-to + `as-of` replay)
- [x] "Shadow mode" adjudication: current vs proposed catalog, returns both decisions + delta
- [x] Batch shadow summary: aggregate verdict-change distribution + dollar impact across many claims
- [ ] Better explanations
  - "Rules that almost fired" capture (for appeals)
  - Rule citation includes source URL (e.g. CMS publication)
- [ ] Eligibility mock interface (proper 270/271 contract behind a swappable adapter)
- [x] Performance: sub-100ms p99 per claim on a 500-rule catalog — **hit at 83ms p99**
- [x] Rule-author CLI: validate, dry-run, diff, shadow

### Phase 3 — Productize (weeks 9–12)

- [x] Multi-tenant data model (per-tenant catalog overlays: add / override / remove)
- [x] API keys per tenant (X-API-Key header → tenant resolution)
- [x] Tenant isolation at the handler layer (each request resolves to its tenant's effective catalog; no shared mutable state in the engine path)
- [x] HTTP API (Reitit + ring-jetty): `/adjudicate`, `/shadow`, `/catalog`, `/catalog/:rule-id`, `/health`, `/version`
- [x] Containerization (multi-stage Dockerfile, non-root runtime, HEALTHCHECK)
- [x] CI/CD pipeline (GitHub Actions: test + container build + smoke test)
- [ ] RBAC inside a tenant (analyst can edit scrubbing rules, admin can edit adjudication)
- [ ] Audit log of every privileged action
- [ ] Web UI for rule authoring (React or HTMX rule library browser + edit-and-test)
- [ ] Prometheus metrics endpoint (per-rule fire counts, decision latencies, error rates)
- [ ] Structured JSON logs with correlation IDs
- [ ] Helm chart for k8s deploy
- [ ] Per-tenant fee schedules + benefit plans (separate from rule overlays)
- [ ] WebSockets for streaming batch adjudication

---

## Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Real CMS rule sources turn out to be harder to parse than expected (PDF, Excel quirks) | Medium | Medium | Time-box ingestion at 1 week per source; if blocked, document and proceed with sample subset |
| Clara performance on a 50,000-rule catalog | Medium | High | Phase 2 includes explicit perf milestone; if Clara struggles, fall back to a hand-tuned classifier that pre-filters before Clara inference |
| Explanation graph storage explodes in size | Medium | Medium | Cap stored "almost fired" rules; everything else is reconstructable by replaying against the rule version |
| Real eligibility (270/271) requires payer relationships we don't have | High | Medium | MVP and Phase 2 use a mock; Phase 3 plans for real integration via a clearinghouse partner |
| Multi-tenant data leakage bug | Low | Catastrophic (HIPAA) | Per-tenant separate database schemas, not row-level isolation; pen-test before any real customer |
| Bitemporal queries get gnarly | Medium | Medium | XTDB has good ergonomics here; allocate explicit learning time in Phase 2 |
| Scope creep — every customer wants custom rules | Certain | Medium | Build the tenant-overlay mechanism early (Phase 3 dependency); resist hardcoded customer logic |

---

## What's deliberately NOT in this plan

- **A general-purpose medical coding NLP/ML layer.** Extracting ICD-10 codes from clinical notes is a real product but a different one. Plug it in upstream via a documented JSON contract; don't build it.
- **Patient-facing functionality.** No patient app, no patient portal, no statements. Adjudication is a B2B function; patient-facing is a different product layer.
- **Full revenue-cycle management.** Posting payments, working A/R, sending statements, collections — that's a billing platform (Greenway, Athena, etc.), not an adjudication engine.
- **In-line ML scoring.** If fraud detection or auto-approval ML enters the picture, it goes in a SEPARATE service that consumes the adjudication decision. Don't bury ML in the rules engine.

---

## Measuring success

| Phase | Definition of done |
|---|---|
| 1 (MVP) | CLI adjudicates a synthetic dental claim correctly, with every rule citation explainable, with test coverage of each rule class. Reviewable in this repo. |
| 2 (Depth) | 500+ real rules running at sub-100ms p99; bitemporal replay works; shadow-mode A/B test against historical claims shows expected behavior. |
| 3 (Productize) | A demo tenant can be onboarded in <1 hour: bring their fee schedule, layer their rules, hit the API, get decisions. Multi-tenant data isolation verified by external pen-test. |

---

## Team shape (if this were a real org)

For Phase 2 + 3, roughly:

- 2 Clojure engineers (engine, API, storage)
- 1 frontend engineer (Phase 3 only)
- 1 DevOps / platform engineer (Phase 3)
- 1 clinical-admin / RCM SME (rule library, customer config; not full-time engineer)
- 0.5 product manager
- 0.5 compliance/security (BAAs, audit, pen-test scheduling)

Total: ~4.5 FTE for 3 months gets you to a demoable product. ~8 FTE for a year gets you to a sellable one.

---

## See also

- [architecture.md](architecture.md) — the existing-repo system that Adjudis sits on top of
- [../06-adjudis-core/README.md](../06-adjudis-core/README.md) — the actual implementation
- [deployment.md](deployment.md) — operational story (extends to Adjudis without major changes)
- [monitoring.md](monitoring.md) — observability story (adds per-rule fire counts)
