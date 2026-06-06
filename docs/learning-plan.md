# Learning plan

The month-by-month plan, broken out from the top-level README so it can evolve independently. Track actual progress against the plan here.

## Month 1–2 — Common Lisp foundations

- [ ] *Practical Common Lisp* (Seibel) — project-oriented read, not cover-to-cover.
- [ ] SBCL installed; editor (SLIME or VS Code + Alive) wired up and used daily.
- [ ] Project 01 shipped with ASDF + tests.

**Definition of done:** can start a fresh CL project from a blank directory in under 10 minutes without looking anything up.

## Month 2–4 — Depth + flagship parser

- [ ] *On Lisp* (Graham) — macros.
- [ ] *Paradigms of AI Programming* (Norvig) — program design, not the AI content per se.
- [ ] Project 03 (macros/CLOS) — built alongside project 02, not after.
- [ ] Project 02 (X12 parser) — v1 shipped (envelope + 837D + validation).

## Month 4 — Domain immersion

- [ ] X12 transaction sets: 837 (esp. 837D), 835, 270/271, 276/277. Notes go in `x12-primer.md`.
- [ ] Clearinghouse / provider / payer / Medicaid flow understood well enough to draw on a whiteboard.
- [ ] Feed the domain learning back into project 02's feature set.

## Month 5 — Clojure

- [ ] *Clojure for the Brave and True* — fast ramp.
- [ ] Project 04 shipped.
- [ ] Comfortable with `deps.edn`, the REPL workflow, and one of `clojure.spec` / Malli.

## Month 6 — MarkLogic + open-source contribution

- [ ] Document-store model and XQuery basics.
- [ ] Project 05 shipped.
- [ ] **At least one merged PR** to a Quicklisp-listed Common Lisp library. Identify the target library by start of month 5 — don't leave this for the last week.

## Tracking

When a checkbox flips, link the commit or PR. This file is the audit trail.
