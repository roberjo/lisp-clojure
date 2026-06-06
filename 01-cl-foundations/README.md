# 01 — Common Lisp foundations

REPL-driven fluency with SBCL + SLIME (or VS Code + Alive). The smallest project in the portfolio, intended to prove the toolchain end-to-end before tackling the X12 parser.

## Status

Not started.

## Deliverable

A small but complete CL application — candidates:

- **Tokenizer** for a simple grammar (lines into typed tokens; useful warm-up for the X12 parser).
- **Mini key-value store** with a pluggable backend (in-memory + file-backed).

Either choice must ship with:

- ASDF system definition (`*.asd`).
- Proper package structure (`defpackage` + `:use`/`:export` discipline; no `:use :cl-user`).
- A test suite using FiveAM or Parachute (pick one and use it across all CL projects in this repo).
- A `Makefile` or `run.sh` wrapper that loads the system and runs tests headless via SBCL.

## Acceptance criteria

- [ ] `sbcl --script run-tests.lisp` exits 0 on green.
- [ ] No reliance on Quicklisp at test-time beyond declared dependencies.
- [ ] README in this directory documents how to load it in SLIME and how to run tests headless.

## Design notes (fill in as you go)

- Why this choice of project, what you'd do differently next time, anything that surprised you about CL's project structure.
