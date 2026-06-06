# Developer onboarding

A guided first week for a new contributor (or your future self after a long break). Assumes the toolchain is installed — see [local-dev-setup.md](local-dev-setup.md) if not.

---

## Day 1: get green, then read

### 0. Verify the environment

```bash
make test
```

If anything red, fix that first — every other onboarding step assumes a working baseline.

### 1. Read in this order

The repo has more code than you need to internalize on day 1. This order minimizes back-tracking:

1. **[../README.md](../README.md)** — what the repo is and why
2. **[architecture.md](architecture.md)** — system view, the seams, the design decisions
3. **[x12-primer.md](x12-primer.md)** — domain background; you can't read the parser without this
4. **[`03-cl-macros-clos/src/segment-def.lisp`](../03-cl-macros-clos/src/segment-def.lisp)** — the `define-segment` macro. Run `(macroexpand-1 '(define-segment ...))` in the REPL to make it concrete.
5. **[`02-x12-parser/src/segments-837d.lisp`](../02-x12-parser/src/segments-837d.lisp)** — see the macro in real use.
6. **[`02-x12-parser/src/parser.lisp`](../02-x12-parser/src/parser.lisp)** — the actual envelope walker. Small.
7. **[`04-clojure-edi-transform/src/edi/transform/core.clj`](../04-clojure-edi-transform/src/edi/transform/core.clj)** — the transducer pipeline.
8. **Whatever you need.** Project 05 and project 01 are independent — read them when you have a reason.

Budget: half a day. If it takes longer, something in the docs is unclear — file an issue.

### 2. Run the pipeline end-to-end

```bash
make e2e
```

Watch the data shape change at each stage. The intermediate forms (EDN, JSON, XML) are the contracts between languages; understanding the shape is more important than memorizing function names.

---

## Day 2: a small, real change

Pick one of these to feel the codebase. Each touches the macro DSL through to the Clojure consumer — exactly the seams that matter.

### Option A — add a new X12 segment

Suppose you want to capture `REF` (reference identification, very common in 837s):

1. Add a declaration in [`02-x12-parser/src/segments-837d.lisp`](../02-x12-parser/src/segments-837d.lisp):

   ```lisp
   (define-segment "REF" (:name :reference-identification)
     (1 :reference-id-qualifier :required t)
     (2 :reference-id :required t))
   ```

2. Add a synthetic-fixture line that includes a `REF*EI*123456789~` segment.

3. Add a `(test parses-ref ...)` case in [`02-x12-parser/tests/parser-tests.lisp`](../02-x12-parser/tests/parser-tests.lisp).

4. `make test-02` — should be green.

Total: ~15 min including the test write. If it takes much longer, the DSL is failing at its job and you've found a bug worth fixing.

### Option B — add a new validation rule

Suppose dental claims require an oral-cavity designation on every SV3:

1. In [`03-cl-macros-clos/src/validate.lisp`](../03-cl-macros-clos/src/validate.lisp), add:

   ```lisp
   (defmethod validate append ((tx dental-claim-transaction))
     (let ((errors '()))
       (dolist (seg (transaction-segments tx))
         (when (string= "SV3" (first seg))
           (let ((cavity (nth 3 seg)))
             (when (or (null cavity) (zerop (length cavity)))
               (push (make-validation-error
                      :segment-id "SV3"
                      :element-position 3
                      :message "SV3 missing oral-cavity designation")
                     errors)))))
       (nreverse errors)))
   ```

2. Add a unit test in [`03-cl-macros-clos/tests/transaction-tests.lisp`](../03-cl-macros-clos/tests/transaction-tests.lisp).

3. `make test-03 && make test-02` — confirm 03 is green AND that the existing 02 fixture still passes (it has cavity = empty string, so this test will fail until you add a cavity to the fixture; that's the bug-finding moment).

### Option C — add a new output target

Suppose downstream wants Avro instead of JSON. In [`04-clojure-edi-transform/src/edi/transform/core.clj`](../04-clojure-edi-transform/src/edi/transform/core.clj):

1. Define `claim->avro` next to `transaction->claim`. (You'll add an Avro dep to `deps.edn`.)
2. Add a `:run-cli-avro` alias to `deps.edn`.
3. Write a test that parses the Avro back to a map and asserts shape.

---

## Day 3+: where to look when something breaks

| Symptom | First place to look |
|---|---|
| Parse fails on a real-looking 837 | [`delimiters.lisp`](../02-x12-parser/src/delimiters.lisp) — wrong delimiter detection masks as wrong segment splits |
| Validation says CLM is missing but it's there | [`parser.lisp`](../02-x12-parser/src/parser.lisp) — `assemble-envelope` mis-attributing segments across ST boundaries |
| `validate` returns nothing on a clearly-broken claim | [`validate.lisp`](../03-cl-macros-clos/src/validate.lisp) — `append` combination expects every method to return a *list*; returning a single error not wrapped in a list silently no-ops |
| Clojure side gets `:type :unknown` for everything | the CL emitter's `*print-case*` binding — should be `:downcase`. See `bin/emit-plist.lisp` |
| XQuery returns empty for clearly-loaded data | namespace mismatch in the XML; every doc must declare `xmlns="urn:x12:837d"` because every query uses the `c:` prefix |
| Test suite passes locally but CI red | line endings on Windows (`.gitattributes` would fix; not added because it hasn't bitten yet) |

---

## Conventions

### Commits

Body wraps at 72 chars. Subject under 60. Co-author tag for Claude-assisted commits.

```
Project NN: short subject line under 60 chars

Body explains the WHY in past tense. Bullet points are fine
for enumeration. Reference files by relative path when
specific.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

### Branches

Single-developer repo so `main` is direct-push. If you fork it for a team, switch to PR-only and require `make test` to pass.

### Tests

- Every public function gets at least one happy-path test.
- Every bug fix gets a regression test in the SAME commit as the fix.
- Macro changes get an expansion test (`is (... (macroexpand-1 ...))`).
- Cross-language changes get an e2e test (`make e2e` plus a manual diff check; no formal e2e harness yet).

### When to add a new project

Don't, unless there's a clear architectural reason. The five projects are deliberately scoped to the job's named stack; a "project 06" would imply scope creep.

### When to factor shared code

Premature. Each project is small enough to read end-to-end in under an hour. Cross-project sharing is the EDN/JSON wire format, which is already the abstraction.

---

## Mental model

The fastest way to internalize this repo is to remember: **each stage is a pure function from one well-defined data shape to another**, with text/streams at the language boundaries.

- 02 = `EDI string → X12-INTERCHANGE object`
- 03 = `X12-INTERCHANGE object → list of validation-error structs`
- 02's emit-plist = `X12-INTERCHANGE → EDN`
- 04 = `EDN → normalized-claim JSON`
- 05 (Python bridge) = `JSON → namespaced XML`
- 05 (XQuery) = `XML doc set + parameters → result XML`

Everything else is plumbing.

---

## Where to ask questions

- Domain (X12 semantics): start with [x12-primer.md](x12-primer.md), then CMS companion guides.
- CL idiom: *Practical Common Lisp* (free, https://gigamonkeys.com/book/) and *On Lisp* (free PDF) cover what's used here.
- Clojure idiom: https://clojure.org/guides and Eric Normand's blog for transducers in particular.
- BaseX: https://docs.basex.org — the docs are actually good.

---

## See also

- [architecture.md](architecture.md) — system design
- [local-dev-setup.md](local-dev-setup.md) — install toolchain
- [../CONTRIBUTING.md](../CONTRIBUTING.md) — PR / commit conventions
