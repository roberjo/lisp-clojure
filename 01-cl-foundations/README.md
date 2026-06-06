# 01 — Common Lisp foundations

A minimal key-value store with in-memory and file-backed backends. The point isn't the KV store — it's to exercise the full CL project shape (ASDF, packages, generic functions, conditions, FiveAM, headless test runner) so the toolchain is proven before tackling project 02.

## Status

Code complete. **Not run** — no SBCL on the dev machine. Verify locally with the commands below.

## Layout

```
01-cl-foundations/
├── kvstore.asd            # ASDF system + test system
├── run-tests.lisp         # headless test entry point
├── src/
│   ├── package.lisp       # single :kvstore package
│   ├── backend.lisp       # STORE class, generics, MISSING-KEY condition
│   ├── memory.lisp        # in-memory backend (hash table)
│   ├── file.lisp          # file-backed backend (subclass of memory + :after methods)
│   └── store.lisp
└── tests/
    ├── package.lisp
    ├── memory-tests.lisp
    ├── file-tests.lisp
    └── suite.lisp
```

## Design notes

- **Generic dispatch over a protocol class**: `store` is the abstract class; `memory-store` and `file-store` are concrete. `kv-get`/`kv-put`/`kv-delete`/`kv-keys`/`kv-count`/`kv-clear` are generics. The `file-store` extends `memory-store` and uses `:after` methods to persist — auxiliary methods earn their keep here because the primary behavior is exactly the in-memory version.
- **Conditions over magic return values**: `kv-get` with no default signals `missing-key`; with a default it returns the default. This mirrors `gethash`'s second-value pattern but lifts it to a condition the caller can `handler-case` against.
- **`with-standard-io-syntax` + `*print-readably*`** in the file backend — anything less and a round-trip through `read` is fragile.

## Running locally

Requires SBCL + Quicklisp (FiveAM).

```sh
# In the project directory:
sbcl --non-interactive --load run-tests.lisp
```

Or in SLIME / Alive:

```lisp
(push #P"/path/to/01-cl-foundations/" asdf:*central-registry*)
(ql:quickload :kvstore/tests)
(asdf:test-system :kvstore)
```

## Acceptance criteria

- [x] ASDF system with separate test system
- [x] Package discipline (`:use #:cl` only; no `:cl-user` leakage)
- [x] FiveAM test suite covering both backends
- [x] Headless runner exits 0/1 for CI
- [ ] Verified green on SBCL (pending local run)
