# Contributing

Single-developer portfolio repo, but if you're picking this up — or if Future You is here after a six-month gap — these are the conventions.

## Before you start

1. Read [docs/onboarding.md](docs/onboarding.md). Don't skip it.
2. Verify the baseline: `make test` (or `.\test-all.ps1` on Windows). All 53 assertions must be green before you touch anything.

## Workflow

1. Branch off `main`. Name: `feature/short-description` or `fix/short-description`.
2. Make the change. Add tests in the same commit, not a follow-up.
3. Run `make test` locally. Also run `make e2e` if your change touches a cross-language seam (the EDN, JSON, or XML wire formats).
4. Open a PR. CI runs the same suites.
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
- Clojure tests: `04-clojure-edi-transform/test/edi/transform/<area>_test.clj`.
- XQuery: no formal tests yet. Add a query script in `05-marklogic-docstore/scripts/test-queries.bxs` if you find a need; the bar is low.

Coverage isn't enforced. Judgment is: every public function should have at least one test that exercises the happy path; every condition raised should have at least one test that asserts it raises.

## Adding a new X12 segment

The walkthrough in [docs/onboarding.md](docs/onboarding.md#option-a--add-a-new-x12-segment) is the canonical example. Five-line summary:

1. `define-segment "ID" (:name :sym) (1 :elt-1 :required t) ...` in `02-x12-parser/src/segments-837d.lisp`.
2. Add it to the synthetic fixture if your tests need to see it parsed.
3. Add a parser/validation test.
4. If the Clojure side cares about this segment, update `04-clojure-edi-transform/src/edi/transform/core.clj` and the corresponding test.
5. `make test && make e2e`.

## Adding a new project

Don't, unless you have a specific architectural case. The five-project structure mirrors the job description and adding more dilutes that signal. If you genuinely need more (e.g. a separate eligibility-API service), open an issue with the case before writing code.

## Style

Each language has its conventions; nothing exotic.

- **CL**: no Lisp-1 style aliases, no exotic reader macros, no `package-inferred-system`. Match the surrounding code. Two-space indent.
- **Clojure**: idiomatic threading, transducers over `->>` for sequence processing, `defn-` for private helpers. Two-space indent.
- **XQuery**: lowercase function names, prefix declarations at the top of every query, two-space indent.
- **Python**: PEP 8; type hints when they help.

Formatters aren't enforced — no `cl-format` or `cljfmt` precommit. Visual consistency with neighbors is the bar.

## What needs human review

Anything that:

- Changes a wire format (EDN, JSON, XML schema, BaseX index definitions).
- Touches a `define-segment` declaration's required-element list.
- Removes or renames an exported symbol.
- Adds a new dependency.

Reviewer for the above: anyone with a checkout that runs `make test` cleanly. (For a single-developer repo, that's you, twice — once writing, once reading the PR.)

## Security

- **Never commit real EDI samples.** The `.gitignore` blocks `samples/*.edi` outside `samples/synthetic/`; don't override it.
- **Never log PHI.** See [docs/monitoring.md](docs/monitoring.md#1-logs-structured-queryable).
- Secrets live in env vars and platform secret stores, never in the repo.

## Where to ask

- Domain: [docs/x12-primer.md](docs/x12-primer.md) → CMS companion guides → an actual claims operations person.
- Lisp idiom: *On Lisp* (Graham), *Practical Common Lisp* (Seibel).
- Clojure idiom: clojure.org/guides, Eric Normand on transducers.
- BaseX: docs.basex.org.

For things not covered: email `jobs@luzontech.com` — the target hiring contact for this portfolio. They know the domain.
