# Contributing

Single-developer portfolio repo, but if you're picking this up — or if Future You is here after a six-month gap — these are the conventions.

## Before you start

1. Read [docs/onboarding.md](docs/onboarding.md). Don't skip it.
2. Verify the baseline: `make test` (or `.\test-all.ps1` on Windows). All ~150 assertions across the five projects must be green before you touch anything.

## Workflow

1. Branch off `main`. Name: `feature/short-description` or `fix/short-description`.
2. Make the change. Add tests in the same commit, not a follow-up.
3. Run `make test` locally. Also run `make e2e` if your change touches a cross-language seam (the EDN, JSON, or XML wire formats). Also run `make adjudicate-demo` if you touched the adjudication path.
4. Open a PR. CI runs the same suites plus a container build + smoke test for project 06.
5. Squash-merge or merge with a clean history. No merge commits.

## Commits

Subject under 60 chars. Body wraps at 72. Explain the **why**, not the **what** — the diff shows the what.

```
Project 02: handle empty repetition separator in ISA

A clearinghouse we don't normally see sends ISA11 as a single
space rather than a real separator character. Per the 5010
spec a single space is reserved, so detect it and fall back
to '^'. Test fixture added in samples/synthetic/.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

When Claude (or any AI assistant) materially helped, add the `Co-Authored-By` trailer. When it didn't, don't.

## Tests

Non-negotiables:

- **Bug fix = regression test in the same commit.** No exceptions.
- **Macro change = expansion test.** Use `is (... (macroexpand-1 ...))`.
- **Cross-language change = end-to-end check.** `make e2e` and visually inspect the XML output.

Test placement:

- CL unit tests: `<project>/tests/<area>-tests.lisp`. Add to the appropriate suite via `(in-suite ...)`.
- Clojure (project 04) tests: `04-clojure-edi-transform/test/edi/transform/<area>_test.clj`.
- Clojure (project 06) tests: `06-adjudis-core/test/adjudis/<area>_test.clj`. Pattern: one test file per source namespace it covers; integration tests live in `api_test.clj`.
- XQuery: no formal tests yet. Add a query script in `05-marklogic-docstore/scripts/test-queries.bxs` if you find a need; the bar is low.

Coverage isn't enforced. Judgment is: every public function should have at least one test that exercises the happy path; every condition raised should have at least one test that asserts it raises.

## Adding a new X12 segment

The walkthrough in [docs/onboarding.md](docs/onboarding.md#option-a--add-a-new-x12-segment) is the canonical example. Five-line summary:

1. `define-segment "ID" (:name :sym) (1 :elt-1 :required t) ...` in `02-x12-parser/src/segments-837d.lisp`.
2. Add it to the synthetic fixture if your tests need to see it parsed.
3. Add a parser/validation test.
4. If the Clojure side cares about this segment, update `04-clojure-edi-transform/src/edi/transform/core.clj` and the corresponding test.
5. `make test && make e2e`.

## Adding a new adjudication rule (project 06)

Two cases:

**A new specific rule in an existing category** — pure data change:

1. Append a map to the appropriate file in `06-adjudis-core/resources/rule-catalog/<category>.edn` (frequency, age-appropriate, pre-auth, eligibility, annual-max, or fee-schedule).
2. `clojure -M:author validate` to schema-check the catalog.
3. Add a test in `06-adjudis-core/test/adjudis/engine_test.clj` asserting the rule fires when expected.
4. `make test-06`.

**A new category** — requires a code change:

1. Add a `defrule` in `06-adjudis-core/src/adjudis/rules.clj` that pattern-matches `CatalogRule` with the new `:category`.
2. Create a new `<category>.edn` under `resources/rule-catalog/` and register it in `catalog.clj`'s `catalog-files` list.
3. Add the category to `author.clj`'s `known-categories` set.
4. Add tests.
5. `make test-06`.

## Adding a new tenant (project 06)

1. Edit `06-adjudis-core/resources/fixtures/tenants.edn`.
2. Add a new entry under `:tenants`: `<tenant-id> {:name :api-key :overlay {:add :override :remove}}`.
3. Add an API test in `api_test.clj` that uses the new key.
4. `make test-06`.

In real production, tenant data isn't in the repo — it's in a per-environment secrets store, with hashed API keys and a database-backed registry.

## Adding a new metric (project 06)

1. Register the metric in `06-adjudis-core/src/adjudis/metrics.clj` (counter, histogram, etc.) with its labels.
2. Add a convenience wrapper function next to `record-auth-attempt!`, `record-adjudication!`, etc. — explicit functions are easier to grep for than ad-hoc registry calls.
3. Call the wrapper at the right point in the handler or engine path.
4. Add a test in `api_test.clj` that scrapes `/metrics` and asserts the metric appears after driving the relevant events.

## Adding a new audit event (project 06)

Use `(log/audit "event_name" :actor … :action … :outcome … :other-context …)` from `adjudis.logging`. The event_name should be snake_case and verb-ish: `auth_failed`, `claim_adjudicated`, `tenant_overlay_applied`. Required fields by convention: `:actor`, `:action`, `:outcome`. Routed automatically to the `audit` logger.

## Adding a new project

Don't, unless you have a specific architectural case. The original five-project structure mirrors the job description. Project 06 was added when the natural next step was a productized SaaS layer; the case for it is in [docs/adjudis-plan.md](docs/adjudis-plan.md). If you genuinely need another project (e.g. a separate eligibility-API service, an admin web UI), document the case the same way before writing code.

## Style

Each language has its conventions; nothing exotic.

- **CL**: no Lisp-1 style aliases, no exotic reader macros, no `package-inferred-system`. Match the surrounding code. Two-space indent.
- **Clojure**: idiomatic threading, transducers over `->>` for sequence processing, `defn-` for private helpers. Two-space indent.
- **XQuery**: lowercase function names, prefix declarations at the top of every query, two-space indent.
- **Python**: PEP 8; type hints when they help.

Formatters aren't enforced — no `cl-format` or `cljfmt` precommit. Visual consistency with neighbors is the bar.

## What needs human review

Anything that:

- Changes a wire format (EDN, JSON, XML schema, BaseX index definitions, the project 06 HTTP request/response shapes).
- Touches a `define-segment` declaration's required-element list.
- Touches a `defrule` in `06-adjudis-core/src/adjudis/rules.clj` (these are the "fixed" categories — changing one affects every tenant).
- Touches the overlay-composition logic in `06-adjudis-core/src/adjudis/tenants.clj` (gets multi-tenant isolation wrong → cross-tenant data leak → severe).
- Removes or renames an exported symbol.
- Adds a new dependency.

Reviewer for the above: anyone with a checkout that runs `make test` cleanly. (For a single-developer repo, that's you, twice — once writing, once reading the PR.)

## Security

- **Never commit real EDI samples.** The `.gitignore` blocks `samples/*.edi` outside `samples/synthetic/`; don't override it.
- **Never log PHI.** Audit log carries claim ids, never patient names / diagnoses / member identifiers. See [docs/monitoring.md](docs/monitoring.md#1-logs-structured-queryable).
- **Tenant data isolation in project 06 is critical.** Every protected handler must derive its catalog from the request's tenant — never reach for a global catalog inside an authenticated path. The `tenant-isolation-acme-rule-doesnt-leak-to-beta` test exists to catch regressions.
- **API keys** in the shipped `tenants.edn` fixture are clearly marked `-dev-only-do-not-use-in-prod`. Real deployments load tenant config from a secrets manager and store hashed keys.
- Secrets live in env vars and platform secret stores, never in the repo.

## Where to ask

- Domain: [docs/x12-primer.md](docs/x12-primer.md) → CMS companion guides → an actual claims operations person.
- Lisp idiom: *On Lisp* (Graham), *Practical Common Lisp* (Seibel).
- Clojure idiom: clojure.org/guides, Eric Normand on transducers.
- BaseX: docs.basex.org.

For things not covered: ask in an issue.
