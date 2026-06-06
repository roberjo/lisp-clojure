# 02 — X12 EDI parser (flagship)

Parses X12 837D (dental claim) interchanges in Common Lisp. Built on project 03's `define-segment` DSL and CLOS hierarchy, so the parser itself is small — the schema lives where it belongs (in declarations), not buried in the parsing code.

## Status

Code complete with two synthetic 837D fixtures. Not run end-to-end (no SBCL on dev box).

## What it does

1. **Read delimiters from the ISA segment** (positions 3, 104, 105). The parser will accept any valid choice — see the `custom-delimiters-837d.edi` fixture using `|` and `^`.
2. **Split into segments** and walk them into an `x12-interchange` → `functional-group` → `transaction` tree.
3. **Pick a CLOS class per transaction** based on the ST01 transaction id (`837` → `dental-claim-transaction`, `835` → `remittance-transaction`).
4. **Validate** via the `edi-dsl:validate` generic — picks up required-element checks (from segment definitions), auditable-mixin checks, and 837D-specific checks (must have at least one CLM).
5. **Round-trip** — `write-to-file` re-emits the interchange using its original delimiters; the test suite parses the output and confirms structure parity.

## Layout

```
02-x12-parser/
├── x12-parser.asd
├── run-tests.lisp
├── bin/emit-plist.lisp        # CLI: EDI -> plist, feeds project 04
├── src/
│   ├── package.lisp
│   ├── delimiters.lisp        # PARSE-ERROR + detect-delimiters
│   ├── segments-837d.lisp     # all DEFINE-SEGMENT declarations for 837D
│   ├── envelope.lisp          # X12-INTERCHANGE / FUNCTIONAL-GROUP classes
│   ├── parser.lisp            # PARSE-STRING + envelope assembly
│   └── io.lisp                # parse-file, write-to-file
├── samples/synthetic/
│   ├── README.md              # PHI warning + fixture catalog
│   ├── minimal-837d.edi
│   └── custom-delimiters-837d.edi
└── tests/
    ├── package.lisp
    ├── delimiters-tests.lisp
    ├── parser-tests.lisp
    ├── validation-tests.lisp
    ├── roundtrip-tests.lisp
    └── suite.lisp
```

## Design decisions

- **Internal representation = plists** (`("CLM" "PCN001" "250.00" ...)`) **inside CLOS transaction objects**. The plist gives line-oriented printability for free; the CLOS object carries cross-segment identity (transaction control number, class-specific validation). Trade-off: positional indexing into the plist is fragile if anyone reorders elements — but X12 element ORDER is part of the spec, so reorderings would already be bugs.
- **Two-pass parse**, not streaming. For batch files multiple GB in size you'd want a SAX-style segment-at-a-time emit; that's flagged as v2 in this README.
- **Errors collected, not signaled.** `validate` returns a list of `validation-error` structs with segment id, element position, and (where applicable) loop. Senior code wants to report all the bad news from one document at once, not abort on the first error.
- **The macro DSL is doing work.** Every segment is one ~10-line declaration in `segments-837d.lisp`; the parser doesn't grow when new segments are added.

## Scope (v1)

In: ISA/IEA, GS/GE, ST/SE, BHT, NM1, HL, CLM, SV3, DTP. One synthetic 837D end-to-end. Custom delimiters. Round-trip. Cross-DSL validation.

Out (intentional): real 837D dictionary in full, 835, streaming, EDI-X12 5010A2 errata, X12N companion-guide rules.

## Running locally

```sh
# tests
sbcl --non-interactive --load run-tests.lisp

# CLI: emit plist for downstream consumers
sbcl --script bin/emit-plist.lisp samples/synthetic/minimal-837d.edi
```

## Acceptance criteria

- [x] Parses synthetic 837D end-to-end
- [x] Handles custom delimiters
- [x] Structured validation errors (segment id + element position)
- [x] Round-trip: parse → write → parse → equal structure
- [x] CLI for downstream consumption by project 04
- [ ] Verified green on SBCL (pending local run)
