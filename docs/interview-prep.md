# Interview prep — talking points mapped to requirements

A working set of answers to the questions a senior Lisp interview is likely to surface. Refine each as the corresponding project ships.

## "Walk me through your Common Lisp experience"

- Frame the portfolio as recent, focused, domain-relevant work — not a substitute for years in seat, but evidence of ramp speed and engineering judgment.
- Lead with project 02 (X12 parser) because it's the on-domain artifact.
- Have a one-minute and a five-minute version ready.

**Concrete artifacts to point at:**

- [`02-x12-parser/src/parser.lisp`](../02-x12-parser/src/parser.lisp) — envelope walker
- [`03-cl-macros-clos/src/segment-def.lisp`](../03-cl-macros-clos/src/segment-def.lisp) — `define-segment` macro
- [`03-cl-macros-clos/src/validate.lisp`](../03-cl-macros-clos/src/validate.lisp) — APPEND method combination
- [`docs/architecture.md`](architecture.md) — full design walk-through

## "Show me a macro you've written and explain why it had to be a macro"

- The `define-segment` DSL from project 03 is the candidate. File: [`03-cl-macros-clos/src/segment-def.lisp`](../03-cl-macros-clos/src/segment-def.lisp).
- Be ready to explain:
  - **Compile-time vs. runtime**: the macro expands once at load time and populates a hash-table registry. The runtime parser does a single hash-table lookup per segment, no walking the registry or interpreting the DSL.
  - **Hygiene**: a `gensym` for the inner binding so the macro can't shadow a caller's variable. There's an explicit test ([`segment-tests.lisp:DEFINE-SEGMENT-EXPANSION-IS-HYGIENIC`](../03-cl-macros-clos/tests/segment-tests.lisp)) that asserts the bound symbol is uninterned.
  - **Why not a data table?** I'd consider it for a one-off, but the DSL plays a second role beyond declaration — it co-locates segment structure with documentation and gives one site that future codegen (parser, validator, codegen for client libraries) can hang off.

## "When would you reach for CLOS vs. plain structs or plists?"

- Project 02 uses a deliberate hybrid: CLOS instances at the transaction level, plain plists inside for per-segment data. Reasoning is in [`docs/architecture.md`](architecture.md#internal-representation-clos-objects-holding-plists).
- Multiple dispatch, method combination, and runtime class redefinition are the real CLOS-only wins. Project 03's `validate` with APPEND method combination is the live example: the `auditable` mixin contributes a check and the `dental-claim-transaction` class contributes another, with no coordination — adding a new mixin is a `defmethod`, not a refactor.

## "How does your CL background transfer to Clojure?"

- Syntax and the REPL workflow transfer immediately.
- What doesn't: macro hygiene model (Clojure's syntax-quote auto-namespacing vs. CL's package system + manual `gensym`), mutability defaults (Clojure is persistent-default), the JVM ecosystem (deps, classpath, GC pressure).
- Project 04 is the evidence. File: [`04-clojure-edi-transform/src/edi/transform/core.clj`](../04-clojure-edi-transform/src/edi/transform/core.clj). Specifically the transducer use (`all-service-lines`) and Malli schemas as data — both more idiomatic than the CL equivalents would be.
- Concrete CL→Clojure gotcha found during build: keyword case mismatch (`:TYPE` vs `:type`) at the EDN boundary. Documented in [x12-primer.md](x12-primer.md#cl-keyword-case--clojure-edn-mismatch).

## "Why is a document store appropriate for EDI?"

- Transactions are self-contained nested documents; the schema is hierarchical and varies by transaction set.
- Relational normalization loses the structure that downstream consumers need.
- XQuery operates on the document tree natively; SQL would require constant joins.
- Concrete artifact: [`05-marklogic-docstore/docs/design.md`](../05-marklogic-docstore/docs/design.md) lays out the document-granularity argument (per-transaction, not per-interchange or per-line).

## "Describe a time you worked in an unfamiliar large codebase"

- Use the open-source CL contribution (target library to be identified by month 5).
- Structure the answer: how you oriented, what you read first, the bug or feature, how you validated.

## "Tell me about a production system you've operated"

- The portfolio doesn't deploy anything, so don't pretend.
- BUT: [`docs/deployment.md`](deployment.md) and [`docs/monitoring.md`](monitoring.md) show the operational model I'd propose if this were productionized — including HIPAA-specific concerns (PHI in logs, BAA chain, encryption posture, audit retention).
- Point at the synthetic-canary section in monitoring.md as the answer to "how would you know if the pipeline silently broke?"

## Questions to ask them

- What does "interface with clearinghouses" mean in their day-to-day — direct sockets, SFTP drops, vendor APIs?
- What's the read/write split with MarkLogic? Reporting reads, transactional writes, or both?
- How do they handle the HIPAA / PHI side — sandbox data, scrubbed prod, synthetic generators?
- What's their batch volume shape — steady-state vs. end-of-month spikes?
- Team size, code review culture, on-call expectations.
- Where do they sit on the streaming-vs-batch parser axis for the biggest 837 files?

## The experience-bar conversation

The 3+ years CL line is the gating risk. Plan: email the hiring contact early with a short note referencing this portfolio, asking how firm the experience requirement is. Don't apply cold against a hard filter.
