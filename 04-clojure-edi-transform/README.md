# 04 — Clojure EDI transform pipeline

Conversational Clojure — the secondary technology in the job description. Consumes the structured output of project 02 and transforms it.

## Status

Not started. Blocked on project 02's output format being stable.

## Deliverable

A Clojure service or CLI that does one of:

- **837 → normalized JSON** for downstream analytics.
- **270/271 eligibility round-trip** (build a 270 request, parse a 271 response).

Either choice must demonstrate:

- Immutability and the seq abstraction (no `atom`s except at clear boundaries).
- **Transducers** for the transform pipeline (not just `->>` threading).
- `clojure.spec` (or Malli — justify the choice) for the input and output schemas.
- A test suite with `clojure.test` or Kaocha.

## Tooling

- `deps.edn` (not Leiningen — deps.edn is the current default).
- Document the JVM version assumed.

## Acceptance criteria

- [ ] `clojure -M:test` runs green.
- [ ] Transform is built with transducers, not `map`/`filter` chains.
- [ ] Spec/Malli failures produce useful, location-bearing error messages.
- [ ] README shows a worked example: input EDI fragment → output JSON.

## Design notes

- Coming from CL: what transferred easily, what didn't.
- Where you reached for JVM interop and why.
