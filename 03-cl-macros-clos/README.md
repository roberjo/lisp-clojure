# 03 — Macros & CLOS abstraction showcase

Metaprogramming and object-modeling work that separates a senior CL developer from a competent user of the language. Naturally co-evolves with project 02 — the segment-definition DSL belongs here.

## Status

Not started.

## Deliverable

Two interlocking pieces:

1. **A small DSL** — a macro `define-segment` (or similar) that declares X12 segment structure and generates parser + validator code. Should demonstrate:
   - Hygienic variable capture (use `gensym` / `with-gensyms`).
   - Compile-time vs. runtime work (segment definitions expand at compile time; only the table lookup happens at runtime).
   - A macro-expansion test, not just behavioral tests.

2. **A CLOS class hierarchy** for transaction types with:
   - Generic functions for the operations (`validate`, `serialize`, `transform-to`).
   - At least one use of method combination beyond the default (`:before` / `:after` / `:around`, or a custom combination).
   - A mixin or two to show non-trivial inheritance.

## Acceptance criteria

- [ ] `(macroexpand-1 '(define-segment ...))` produces readable, hygienic code.
- [ ] CLOS hierarchy is used by project 02 (not invented in isolation).
- [ ] Tests cover both the macro expansion and the generated code's runtime behavior.

## Design notes

- Why a macro DSL beats a data-driven table lookup here (or doesn't).
- What CLOS gives you that protocols/interfaces in Clojure don't.
