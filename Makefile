# Run the full test suite for the portfolio.
#
# Requires: sbcl + quicklisp, clojure CLI, basex (for the e2e demo).
# All checked-in tests pass against:
#   SBCL 2.6.5, deps.clj/clojure 1.12.5, Java 24, BaseX 12.0.

SBCL    ?= sbcl
CLOJURE ?= clojure

.PHONY: all test test-01 test-02 test-03 test-04 test-06 bench-06 \
        e2e adjudicate-demo \
        uberjar-06 docker-build-06 serve-06 \
        clean help

all: test

test: test-01 test-03 test-02 test-04 test-06
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

test-06:
	@echo "=== Project 06: adjudis-core ==="
	cd 06-adjudis-core && $(CLOJURE) -X:test

bench-06:
	@echo "=== Project 06: benchmark ==="
	cd 06-adjudis-core && $(CLOJURE) -M:bench

uberjar-06:
	@echo "=== Project 06: uberjar ==="
	cd 06-adjudis-core && $(CLOJURE) -T:build uber

docker-build-06: uberjar-06
	@echo "=== Project 06: docker image ==="
	docker build -t adjudis-core:0.1.0 -f 06-adjudis-core/Dockerfile 06-adjudis-core

serve-06:
	@echo "=== Project 06: starting HTTP API on PORT=$${PORT:-8080} ==="
	cd 06-adjudis-core && $(CLOJURE) -M:serve

# End-to-end: EDI -> CL plist -> JSON -> XML.
# Requires Python 3 on PATH.
e2e:
	@echo "=== Pipeline: EDI -> EDN -> JSON -> XML ==="
	cd 02-x12-parser && $(SBCL) --script bin/emit-plist.lisp samples/synthetic/minimal-837d.edi \
	  | (cd ../04-clojure-edi-transform && $(CLOJURE) -M:run-cli) \
	  | python3 05-marklogic-docstore/scripts/from-json.py --multi

# Adjudicate the e2e-produced claim against the rule library.
# Requires the full pipeline to be runnable.
adjudicate-demo:
	@echo "=== Pipeline + Adjudication ==="
	cd 02-x12-parser && $(SBCL) --script bin/emit-plist.lisp samples/synthetic/minimal-837d.edi \
	  | (cd ../04-clojure-edi-transform && $(CLOJURE) -M:run-cli) \
	  | (cd ../06-adjudis-core && $(CLOJURE) -M:run-cli \
	       --member resources/fixtures/member-doe-jane.edn \
	       --history resources/fixtures/history-doe-jane.edn)

clean:
	@echo "Nothing to clean (no build artifacts checked in)."

help:
	@echo "Targets:"
	@echo "  test              run all five test suites (default)"
	@echo "  test-NN           run a specific project's suite"
	@echo "  bench-06          run the project 06 adjudication benchmark"
	@echo "  e2e               run EDI -> EDN -> JSON -> XML pipeline"
	@echo "  adjudicate-demo   run the pipeline through adjudication"
	@echo "  uberjar-06        build the adjudis-core standalone jar"
	@echo "  docker-build-06   build the adjudis-core container image"
	@echo "  serve-06          start the adjudis-core HTTP API"
