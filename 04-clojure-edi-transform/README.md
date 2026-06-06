# 04 — Clojure EDI transform pipeline

Consumes project 02's plist-as-EDN output and emits normalized JSON claims. Built with transducers and Malli; deps.edn project, no Leiningen.

## Status

Green on Clojure 1.12 (deps.clj 1.12.5) + Java 24. 4 tests, 21 assertions. End-to-end EDI → JSON pipeline verified.

## Pipeline

```
.edi file
  │
  │  sbcl --script ../02-x12-parser/bin/emit-plist.lisp <file>
  ▼
EDN plist (one per transaction, line-delimited)
  │
  │  clojure -M:run-cli
  ▼
JSON claim (one per line, on stdout)
```

## Why transducers, not `->>`?

The transform is segment-oriented: pick segments of interest, map each into a sub-structure, collapse into a claim record. `all-service-lines` is the canonical example — it's a `(comp (filter ...) (map-indexed ...))` xform that works identically over an in-memory vector today and would work over a `core.async` channel tomorrow if 02's emitter were rewritten as a stream. No changes to the transform itself.

## Why Malli, not clojure.spec?

- Schemas are plain data (vectors and maps), so they're inspectable and printable.
- Error messages out of the box are much more useful than `s/explain-data`.
- Function-instrumentation story is cleaner if the project grows in that direction.

clojure.spec would have worked; Malli is the better default for new projects today.

## Layout

```
04-clojure-edi-transform/
├── deps.edn
├── src/edi/transform/
│   ├── schema.clj       # Malli schemas: Transaction (input), NormalizedClaim (output)
│   ├── core.clj         # the transform itself
│   └── cli.clj          # stdin EDN -> stdout JSON
├── resources/fixtures/
│   └── minimal-837d.edn # mirror of 02's minimal fixture, hand-translated
└── test/edi/transform/
    └── core_test.clj
```

## Running locally

```sh
# Install the Clojure CLI (https://clojure.org/guides/install_clojure).

# Tests:
clojure -X:test

# End-to-end (requires SBCL + the x12-parser system loadable):
sbcl --script ../02-x12-parser/bin/emit-plist.lisp ../02-x12-parser/samples/synthetic/minimal-837d.edi \
  | clojure -M:run-cli
```

## Acceptance criteria

- [x] Uses transducers (`all-service-lines` is `(comp (filter) (map-indexed))`)
- [x] Malli schemas on input and output, with a `validate-claim` hook on the CLI
- [x] Test suite covers map conversion, full-fixture transform, transducer composition
- [x] Worked example documented above
- [x] Verified green on Clojure 1.12 via deps.clj

## Coming from CL

What transferred without thinking: REPL workflow, value-oriented design, persistent data structures map directly onto plists.

What was new: namespaces vs. CL packages (Clojure's namespaces are not a value, no easy `find-package`), macro syntax-quote vs. CL's backquote/`gensym` (Clojure auto-namespaces; CL doesn't), and the JVM startup cost — much more friction than `sbcl --script`.
