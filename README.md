# lisp-edi-portfolio

[![test](https://github.com/roberjo/lisp-clojure/actions/workflows/test.yml/badge.svg)](https://github.com/roberjo/lisp-clojure/actions/workflows/test.yml)

A learning roadmap and project portfolio for qualifying as a **Senior Lisp Software Engineer** on a healthcare EDI team. This repo demonstrates Common Lisp depth, working Clojure, and familiarity with document-oriented data (MarkLogic / XML) — applied to the real domain of X12 healthcare EDI (claims, remittance, eligibility).

> **Target role:** Design, create, and maintain software interfacing with clearinghouses, Medicaid agencies, and plan sponsors using Lisp, Clojure, and MarkLogic.

---

## Status

All five projects verified green on:

- SBCL 2.6.5 (projects 01, 02, 03) — 32 tests across the three CL projects
- deps.clj / Clojure 1.12.5 + Java 24 (project 04) — 4 tests, 21 assertions
- BaseX 12.0 (project 05) — sample docs loaded, three queries return expected results
- Python 3.13 (project 04 → 05 bridge)

| # | Project | Tests | Status |
|---|---|---|---|
| 01 | CL kvstore | 8 / 8 | ✅ green |
| 02 | X12 837D parser | 15 / 15 | ✅ green |
| 03 | Macros + CLOS DSL | 9 / 9 | ✅ green |
| 04 | Clojure transform | 21 / 21 | ✅ green |
| 05 | XQuery docstore | n/a (queries) | ✅ runs against BaseX |
| 06 | Adjudis core (Clara + versioning + shadow + HTTP API + multi-tenant) | 54 / 139 | ✅ green |

**Cross-project pipeline (verified end-to-end):**

```
.edi ─[02 emit-plist.lisp]─▶ EDN ─[04 cli.clj]─▶ JSON ─┬─▶ [05 from-json.py]─▶ XML ─[BaseX]─▶ XQuery results
                                                       │
                                                       └─▶ [06 adjudis cli]──▶ adjudication decision JSON
                                                                                (with rule citations)
```

Run everything: `make test` (or `./test-all.ps1` on Windows). Run the storage pipeline: `make e2e`. Run the adjudication pipeline: `make adjudicate-demo`.

---

## Quick links

**Get started:**
- [docs/local-dev-setup.md](docs/local-dev-setup.md) — install the toolchain (Windows / macOS / Linux)
- [docs/onboarding.md](docs/onboarding.md) — guided first week
- [CONTRIBUTING.md](CONTRIBUTING.md) — workflow, commit style, conventions

**Understand the design:**
- [docs/architecture.md](docs/architecture.md) — system view, seams, key decisions, failure modes
- [docs/x12-primer.md](docs/x12-primer.md) — EDI domain background + gotchas surfaced building this

**Operational thinking** (portfolio-scoped; nothing deployed):
- [docs/deployment.md](docs/deployment.md) — what production would look like
- [docs/monitoring.md](docs/monitoring.md) — observability story

**Career-facing:**
- [docs/learning-plan.md](docs/learning-plan.md) — the 6-month plan + what's done
- [docs/interview-prep.md](docs/interview-prep.md) — talking points mapped to artifacts

**Building beyond the base portfolio:**
- [docs/adjudis-plan.md](docs/adjudis-plan.md) — full 3-month plan for a claim-adjudication platform on top of the existing pipeline (project 06 is its MVP)

---

## Why this repo exists

The job's requirements break into two kinds of gaps:

1. **Demonstrable Common Lisp skill** — covered directly by the projects here.
2. **Years of experience** (3+ CL, 5+ software, 2+ large-codebase) — partly a matter of track record, but this repo is structured so each project is non-trivial, multi-module, and dependency-aware to evidence large-codebase competence rather than toy snippets.

Each project below is a directory in this repo with its own README, tests, and a short write-up of design decisions (the kind of reasoning a senior engineer is expected to articulate in an interview).

---

## Repository layout

```
lisp-edi-portfolio/
├── README.md                      # this file
├── CONTRIBUTING.md                # workflow, commit style
├── Makefile                       # `make test`, `make e2e`
├── test-all.ps1                   # Windows equivalent
├── 01-cl-foundations/             # REPL-driven CL fundamentals (kvstore)
├── 02-x12-parser/                 # FLAGSHIP: Common Lisp X12 837D parser
├── 03-cl-macros-clos/             # macros + CLOS abstraction (used by 02)
├── 04-clojure-edi-transform/      # Clojure pipeline transforming parsed EDI
├── 05-marklogic-docstore/         # BaseX/XQuery doc-store modeling
├── 06-adjudis-core/               # Clara-based claim adjudication engine (MVP)
└── docs/
    ├── architecture.md            # technical deep dive
    ├── adjudis-plan.md            # full 3-month plan for project 06
    ├── local-dev-setup.md         # per-OS install + verification
    ├── onboarding.md              # guided first week for a new dev
    ├── deployment.md              # operational design (if productionized)
    ├── monitoring.md              # observability story
    ├── learning-plan.md           # the month-by-month plan
    ├── x12-primer.md              # EDI/X12 domain notes + gotchas
    └── interview-prep.md          # talking points mapped to artifacts
```

---

## The projects

### 01 — Common Lisp foundations
**Goal:** Establish REPL-driven fluency with SBCL + SLIME/Alive.
**Deliverable:** A small but complete CL application (e.g. a tokenizer or a mini key-value store) with ASDF system definition, package structure, and a test suite (FiveAM or Parachute).
**Showcases:** Idiomatic CL, project structure, dependency management via Quicklisp.

### 02 — X12 EDI parser *(flagship project)*
**Goal:** Parse, validate, and structurally transform X12 healthcare EDI documents in Common Lisp. This is the centerpiece — it maps directly to "interfaces with clearinghouses."
**Scope:**
- Parse the ISA/GS/ST envelope structure and segment/element/sub-element hierarchy.
- Support the **837D** (dental claim — relevant to a dental benefits manager), with **835** (remittance) as a stretch.
- Validate segment ordering and required elements; emit structured error reports.
- Transform parsed segments into a clean internal representation (plists / CLOS objects).

**Showcases:** Real-domain CL across a sizeable codebase, parsing/state handling, error design, upstream/downstream awareness (the parser is the seam between a clearinghouse and internal systems).

### 03 — Macros & CLOS abstraction showcase
**Goal:** Demonstrate the metaprogramming and object-modeling that separates a senior CL dev from a user of the language.
**Deliverable:** A small DSL — for example, a macro that declares X12 segment definitions and generates parser/validator code, plus a CLOS class hierarchy for transaction types using generic functions and method combination.
**Showcases:** Macro hygiene, code generation, CLOS, designing abstractions for others to use.

### 04 — Clojure EDI transform pipeline
**Goal:** Be conversational in Clojure (a listed secondary technology).
**Deliverable:** A Clojure service/CLI that consumes the structured output of project 02 and transforms it — e.g. 837 claim data into a normalized JSON document, or an eligibility (270/271) round-trip. Emphasize immutability, the seq abstraction, transducers, and `clojure.spec`.
**Showcases:** Functional idioms, JVM interop awareness, data transformation.

### 05 — MarkLogic / document-store modeling
**Goal:** Show why a document database fits document-centric EDI, and basic competence with the model.
**Deliverable:** Schema/document design for storing EDI transactions as XML/JSON documents, a set of XQuery/XPath queries (eligibility lookups, claim status), and notes on indexing. Use MarkLogic Community Edition or a documented equivalent.
**Showcases:** Document modeling, XQuery, connecting the data model back to the EDI domain.

---

## Learning plan (6 months)

A realistic sequence. Adjust pace to your available hours; the ordering matters more than the calendar.

### Month 1–2 — Common Lisp foundations
- Work through *Practical Common Lisp* (Seibel, free online), project-oriented.
- Set up SBCL + Emacs/SLIME (or VS Code + Alive). Live in the REPL daily.
- Build **project 01**. Commit incrementally so the repo shows steady progress.

### Month 2–4 — Depth + flagship parser
- Read *On Lisp* (Graham) for macros and *Paradigms of AI Programming* (Norvig) for program design.
- Build **project 03** (macros/CLOS) alongside, then **project 02** (X12 parser) as the main effort.
- Study the X12 domain in parallel (see `docs/x12-primer.md`).

### Month 4 — EDI / healthcare domain
- Learn X12 transaction sets: **837** (claims, esp. 837D dental), **835** (remittance), **270/271** (eligibility), **276/277** (claim status).
- Understand clearinghouses and the provider ↔ payer ↔ Medicaid agency exchange.
- Feed this directly into project 02's feature set.

### Month 5 — Clojure
- *Clojure for the Brave and True* (free online) for a fast ramp.
- Build **project 04**. Coming from CL, syntax transfers quickly; focus on JVM ecosystem and functional idioms.

### Month 6 — MarkLogic + open-source contribution
- Learn the document-store model and XQuery basics (MarkLogic free developer resources).
- Build **project 05**.
- Contribute to an open-source Common Lisp project (a Quicklisp library) to evidence real-world work across an existing large codebase with dependencies — directly addressing requirement #3.

---

## Mapping projects to the job requirements

| Requirement | Where this repo addresses it |
|---|---|
| 3+ yrs Common Lisp | Projects 01–03 demonstrate idiomatic CL, macros, CLOS, and a substantial domain application. Open-source CL contributions add real-world depth. |
| 5+ yrs software development | Existing professional experience (frame it in the application); this repo evidences current, active engineering breadth. |
| 2+ yrs large codebase, upstream/downstream dependencies | Project 02 is explicitly the integration seam between clearinghouses and internal systems; open-source contribution shows working within an existing large codebase. |
| Lisp / Clojure / MarkLogic | Projects 02–03 (Lisp), 04 (Clojure), 05 (MarkLogic). |
| Healthcare EDI domain | X12 837/835/270/271 throughout; `docs/x12-primer.md`. |

---

## Resources

- *Practical Common Lisp* — Peter Seibel (free online)
- *On Lisp* — Paul Graham (free PDF)
- *Paradigms of AI Programming* — Peter Norvig
- *Clojure for the Brave and True* — Daniel Higginbotham (free online)
- Quicklisp (CL library manager), SBCL, FiveAM/Parachute (testing)
- X12 healthcare transaction set references; MarkLogic developer documentation

---

## A note on the experience bar

The "3+ years Common Lisp" requirement is steep and is largely time-in-seat that a study plan can't manufacture. This repo is the strongest substitute: concrete, non-trivial, domain-relevant work. It is worth emailing early to ask how firm that line is — many teams flex it for strong generalist engineers who can clearly ramp, and this portfolio is the evidence that you can.
