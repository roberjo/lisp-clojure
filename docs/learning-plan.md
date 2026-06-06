# Learning plan

The month-by-month plan, broken out from the top-level README so it can evolve independently. Track actual progress against the plan here.

## Snapshot (current state)

All five project skeletons + working implementations + green test suites exist. The plan below now serves as a depth-roadmap: where to invest beyond the working baseline.

| Project | Baseline shipped | Next depth investments |
|---|---|---|
| 01 kvstore | ✅ ASDF + FiveAM | optional: add an LMDB-backed implementation to demonstrate FFI |
| 02 X12 parser | ✅ 837D, two fixtures, round-trip | add 835 (remittance), stream-mode parser, real-spec 837D coverage beyond the 11-segment slice |
| 03 macros/CLOS | ✅ define-segment + APPEND combination | add a `define-loop` macro for X12 loop structure; demonstrate `:around` methods more substantively |
| 04 Clojure | ✅ deps.edn + Malli + transducers | core.async pipeline; clojure.spec.gen.alpha for generative tests |
| 05 docstore | ✅ BaseX + 3 queries + bridge | MarkLogic deployment (developer license); add 270/271 eligibility round-trip; index tuning experiments |



## Month 1–2 — Common Lisp foundations

- [x] SBCL installed; project 01 shipped with ASDF + tests; full toolchain (Quicklisp, FiveAM, run-tests.lisp) proven end-to-end.
- [ ] *Practical Common Lisp* (Seibel) — project-oriented read, not cover-to-cover.
- [ ] Editor (SLIME / SLY / VS Code + Alive) wired up and used daily.

**Definition of done:** can start a fresh CL project from a blank directory in under 10 minutes without looking anything up.

## Month 2–4 — Depth + flagship parser

- [x] Project 03 (macros/CLOS) — `define-segment` + APPEND-combined `validate` + CLOS hierarchy shipped.
- [x] Project 02 (X12 parser) — v1 shipped (envelope + 837D + validation + round-trip).
- [ ] *On Lisp* (Graham) — macros.
- [ ] *Paradigms of AI Programming* (Norvig) — program design, not the AI content per se.

## Month 4 — Domain immersion

- [x] Initial 837D coverage (11 segments) with structured validation.
- [ ] Add 835 (remittance), 270/271 (eligibility).
- [ ] Clearinghouse / provider / payer / Medicaid flow understood well enough to draw on a whiteboard.
- [ ] Feed the domain learning back into project 02's feature set.

## Month 5 — Clojure

- [x] Project 04 shipped (deps.edn + Malli + transducers + JSON CLI).
- [ ] *Clojure for the Brave and True* — fast ramp.
- [ ] Comfortable with the REPL workflow at SLY/CIDER level fluency.
- [ ] Generative tests via clojure.spec.gen.alpha or Malli generators.

## Month 6 — MarkLogic + open-source contribution

- [x] Project 05 shipped against BaseX (open-source XQuery engine; data model and queries port to MarkLogic).
- [ ] Document-store model and XQuery basics — pick a textbook chapter and work through it.
- [ ] Migrate project 05 to a MarkLogic developer license deployment.
- [ ] **At least one merged PR** to a Quicklisp-listed Common Lisp library. Identify the target library by start of month 5 — don't leave this for the last week.

## Tracking

When a checkbox flips, link the commit or PR. This file is the audit trail.
