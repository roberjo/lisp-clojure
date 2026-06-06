# Run the full test suite for the portfolio.
#
# Requires: sbcl + quicklisp, clojure CLI, basex (for the e2e demo).
# All checked-in tests pass against:
#   SBCL 2.6.5, deps.clj/clojure 1.12.5, Java 24, BaseX 12.0.

SBCL    ?= sbcl
CLOJURE ?= clojure

.PHONY: all test test-01 test-02 test-03 test-04 e2e clean help

all: test

test: test-01 test-03 test-02 test-04
	@echo
	@echo "All test suites passed."

test-01:
	@echo "=== Project 01: kvstore ==="
	cd 01-cl-foundations && $(SBCL) --non-interactive --load run-tests.lisp

# 03 is built before 02 because 02's tests depend on it being loadable.
test-03:
	@echo "=== Project 03: edi-dsl ==="
	cd 03-cl-macros-clos && $(SBCL) --non-interactive --load run-tests.lisp

test-02:
	@echo "=== Project 02: x12-parser ==="
	cd 02-x12-parser && $(SBCL) --non-interactive --load run-tests.lisp

test-04:
	@echo "=== Project 04: clojure transform ==="
	cd 04-clojure-edi-transform && $(CLOJURE) -X:test

# End-to-end: EDI -> CL plist -> JSON -> XML.
# Requires Python 3 on PATH.
e2e:
	@echo "=== Pipeline: EDI -> EDN -> JSON -> XML ==="
	cd 02-x12-parser && $(SBCL) --script bin/emit-plist.lisp samples/synthetic/minimal-837d.edi \
	  | (cd ../04-clojure-edi-transform && $(CLOJURE) -M:run-cli) \
	  | python3 05-marklogic-docstore/scripts/from-json.py --multi

clean:
	@echo "Nothing to clean (no build artifacts checked in)."

help:
	@echo "Targets:"
	@echo "  test     run all four test suites (default)"
	@echo "  test-NN  run a specific project's suite"
	@echo "  e2e      run the cross-language pipeline end-to-end"
