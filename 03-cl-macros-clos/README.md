# 03 — Macros & CLOS abstraction

A `define-segment` DSL that declares X12 segment structure at compile time, plus a CLOS hierarchy for transaction sets using method combination. Used by project 02.

## Status

Code complete. Not run end-to-end (no SBCL on dev box).

## What's here

### The macro: `define-segment`

```lisp
(define-segment "NM1" (:name :individual-or-organizational-name)
  (1 :entity-identifier-code :required t)
  (2 :entity-type-qualifier  :required t)
  (3 :name-last-or-organization-name :required t)
  (4 :name-first)
  (8 :identification-code-qualifier)
  (9 :identification-code))
```

Expands at compile time into a registered `segment-definition` instance. Key points:

- **Hygienic**: the macro `gensym`s its temporary variable so it can't shadow a `def` or `let` at the call site. There's an explicit `macroexpand-1` test that asserts the bound variable is uninterned.
- **Compile-time vs. runtime**: the element list is built by quoting forms; only the `make-instance` runs at load time. The registry hash table lookup is the only thing the parser pays for at request time.
- **Why a macro, not a data table?** A data table would be functionally equivalent for the simple case, but the macro form gives one declaration site that can later expand into both the validator and a generated parser function (planned). The macro is also self-documenting at the call site in a way an `(add-segment ...)` call isn't.

### The CLOS hierarchy

```
transaction
├── claim-transaction (also auditable)
│   └── dental-claim-transaction
└── remittance-transaction (also auditable)

auditable          ← mixin, contributes control-number validation
```

`validate` uses the **`append` method combination**. Every applicable method returns a list of errors and the framework concatenates them. Adding a new mixin (`sox-auditable`, `phi-tagged`) requires no changes to existing classes — just a new `defmethod validate append (...)`.

Standard dispatch is used elsewhere:
- `serialize` — single method on the root, parameterized by delimiters.
- `transform-to` — `eql`-specialized on the target keyword (`:plist`), so adding a new target is a new method, not a `case` branch.

## Running locally

```sh
sbcl --non-interactive --load run-tests.lisp
```

## Acceptance criteria

- [x] `(macroexpand-1 '(define-segment ...))` produces readable, hygienic code (test covers this).
- [x] CLOS hierarchy used by project 02 (segment definitions imported there).
- [x] Tests cover macro expansion AND generated/runtime behavior.
- [ ] Verified green on SBCL (pending local run).
