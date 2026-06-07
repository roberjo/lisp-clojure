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
- Project 04 is the evidence for the basic Clojure-fluency answer. File: [`04-clojure-edi-transform/src/edi/transform/core.clj`](../04-clojure-edi-transform/src/edi/transform/core.clj). Specifically the transducer use (`all-service-lines`) and Malli schemas as data — both more idiomatic than the CL equivalents would be.
- Project 06 is the evidence for **production-shape** Clojure. Reitit routing, Clara rules engine (with type-driven dispatch via defrecord), tools.build uberjar, logback + iapetos, multi-tenant data model. The same language can do both "small data pipeline stage" (project 04) and "full SaaS-shaped service" (project 06).
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

- The portfolio doesn't deploy anything to a real environment, so don't pretend.
- BUT: project 06 ships with most of the productionization scaffolding — Dockerfile, GitHub Actions CI (test + container build + `/health` smoke test on every push), structured JSON logs + correlation IDs + audit log via logback, Prometheus metrics at `/metrics`, multi-tenant isolation, healthchecks. Walk through [`06-adjudis-core/README.md`](../06-adjudis-core/README.md) live.
- For the broader pipeline (projects 02–05), [`docs/deployment.md`](deployment.md) and [`docs/monitoring.md`](monitoring.md) show the operational model I'd propose if those were productionized to the same shape — including HIPAA-specific concerns (PHI in logs, BAA chain, encryption posture, audit retention).
- Point at the synthetic-canary section in monitoring.md as the answer to "how would you know if the pipeline silently broke?"

## "Show me a rules engine you've worked with"

- Project 06's adjudication engine: Clara (forward-chaining Rete-based) productions in [`06-adjudis-core/src/adjudis/rules.clj`](../06-adjudis-core/src/adjudis/rules.clj).
- Architectural punchline: **rule productions are code; rule instances are data.** Six productions (one per category — frequency, age-appropriate, pre-auth, eligibility, annual-max, fee-schedule), N data rules in `resources/rule-catalog/*.edn`. Adding a specific rule is a single map; adding a category is a new production.
- Why this shape: real customers have clinical-admin staff editing rules. The data path is what they edit; the code path is owned by engineering.
- Performance: hits sub-100ms p99 on a 500-rule catalog (`make bench-06`). Clara's alpha-network pruning is why this scales sub-linearly.
- Versioning + shadow mode: every rule carries optional effective-from/to; the engine accepts an `as-of` parameter for historical replay. Shadow mode (`/shadow` endpoint, `adjudis.author shadow` CLI) runs a claim against current AND proposed catalogs and returns both decisions + delta, so PMs A/B-test changes against real historical claims before promoting.

## "Walk me through a multi-tenant architecture you've designed"

- Project 06's overlay model: every tenant has an EDN overlay with `:add`, `:override`, `:remove`. The composition function ([`tenants/apply-overlay`](../06-adjudis-core/src/adjudis/tenants.clj)) computes the effective catalog at request time: `(base - remove - override-ids) + override + add`.
- Tenant isolation is **by data flow, not by trust**: the engine takes the effective catalog as an argument. The engine itself doesn't know about tenants. A regression would be visible in the [`tenant-isolation-acme-rule-doesnt-leak-to-beta`](../06-adjudis-core/test/adjudis/api_test.clj) test.
- Auth: API-key middleware resolves `X-API-Key` → tenant; protected routes get `::tenant` in the request map; handlers compute the tenant's effective catalog from it. Public routes (`/health`, `/version`, `/metrics`) skip auth.
- HIPAA-relevant: a multi-tenant data leak in healthcare is a reportable breach. The architecture makes the leak hard to write because there's no global mutable catalog the engine could accidentally see.

## "Walk me through your observability story"

- Project 06 is the live evidence; [`docs/monitoring.md`](monitoring.md) is the broader plan.
- Every HTTP request gets a correlation ID (`X-Request-Id` echoed in response; same id appears as `request_id` on every log line for that request via SLF4J MDC). Every authenticated request also gets `tenant_id` on the log lines.
- Two logger streams: application logs and a dedicated `audit` logger (own appender, separate retention — designed for the 6yr+ HIPAA audit-trail requirement).
- Metrics: iapetos at `/metrics` in Prometheus exposition format. Adjudis-specific counters (auth attempts by outcome, adjudications by tenant+verdict, findings by category+severity), histograms for HTTP latency and adjudication duration, JVM defaults.
- No PHI in logs. Claim ids and structural facts only.
- What's deliberately not yet shipped: OpenTelemetry traces (sketched but not implemented).

## Questions to ask them

- What does "interface with clearinghouses" mean in their day-to-day — direct sockets, SFTP drops, vendor APIs?
- What's the read/write split with MarkLogic? Reporting reads, transactional writes, or both?
- How do they handle the HIPAA / PHI side — sandbox data, scrubbed prod, synthetic generators?
- What's their batch volume shape — steady-state vs. end-of-month spikes?
- Team size, code review culture, on-call expectations.
- Where do they sit on the streaming-vs-batch parser axis for the biggest 837 files?

## The experience-bar conversation

The 3+ years CL line is the gating risk. Plan: email the hiring contact early with a short note referencing this portfolio, asking how firm the experience requirement is. Don't apply cold against a hard filter.
