# 05 — Document-store modeling (MarkLogic or equivalent)

Why a document database fits document-centric EDI, and basic competence with the model.

## Status

Not started.

## Open question — pick a backend

MarkLogic Community Edition is no longer freely available the way it once was. Three workable paths:

1. **MarkLogic Developer License** — free for development, requires registration. Most directly aligns with the job's stated stack.
2. **BaseX** — Apache-licensed, native XQuery/XPath, runs anywhere. Closest open-source analogue; skills transfer.
3. **eXist-db** — LGPL, XQuery-native, mature.

Decide and document the choice in this README. If you go with BaseX/eXist-db, the interview talking point is: "I built it on an open-source XQuery engine because licensing; the data model and queries port directly to MarkLogic."

## Deliverable

- Schema/document design for storing EDI transactions as XML (and/or JSON).
- A set of XQuery / XPath queries covering realistic operations:
  - Eligibility lookup by member id.
  - Claim status by claim id.
  - Aggregate: claim counts by payer, by date range.
- Notes on indexing (range indexes, path indexes, element-value indexes).
- A loader script that ingests output from project 02 or 04.

## Acceptance criteria

- [ ] Document design diagram (or a representative XML fixture) committed.
- [ ] Queries are in `.xqy` files, runnable against the chosen backend.
- [ ] README explains why a doc store beats a relational schema for X12.

## Design notes

- Index choices and what queries they enable.
- How envelope/transaction nesting maps to documents (one doc per transaction? per interchange?).
