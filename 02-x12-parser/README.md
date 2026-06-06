# 02 — X12 EDI parser (flagship)

Parse, validate, and structurally transform X12 healthcare EDI documents in Common Lisp. This is the centerpiece of the portfolio — it maps directly to "interfaces with clearinghouses, Medicaid agencies, and plan sponsors."

## Status

Not started.

## Scope

**In scope (v1):**

- ISA/GS/ST envelope parsing with correct delimiter detection (delimiters are positional in the ISA segment, not fixed).
- Segment / element / sub-element hierarchy as a clean internal representation (plists or CLOS objects — decide and justify).
- **837D** (dental claim) transaction set — relevant to a dental benefits manager.
- Segment-ordering validation and required-element validation, with structured error reports (not just signaled conditions — collectable, location-bearing errors).
- Round-trip: parse → in-memory → re-serialize identical bytes (for a well-formed input).

**Stretch:**

- **835** (remittance advice).
- Streaming parser for large batch files.

**Out of scope:**

- Full X12 coverage. Pick a tight slice and do it well.

## Sample data

X12 spec PDFs are not free, but public sample files exist. Sources to investigate:

- CMS companion guides (free, define plan-specific usage of 837/835).
- Public clearinghouse sandbox docs.
- The `samples/` directory is gitignored by default — never commit real claim data (PHI risk).

A `samples/synthetic/` subdirectory IS committed, for hand-crafted fixtures with fake data.

## Acceptance criteria

- [ ] Parses a synthetic 837D fixture end-to-end.
- [ ] Validation errors include segment id, loop, and element position.
- [ ] Test suite covers: envelope edge cases (custom delimiters), malformed input, missing required elements.
- [ ] README explains the loop/segment/element model in 5 lines for a reader who has never seen X12.

## Design notes

- Document the choice between plists vs. CLOS for the internal model.
- Note where the macro DSL from project 03 plugs in (segment definitions → generated parser/validator code).
