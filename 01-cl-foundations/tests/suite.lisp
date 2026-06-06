(in-package #:kvstore/tests)

;;; NB: ALL-TESTS is the parent; the per-file suites must declare ALL-TESTS
;;; as their parent (:in-suite) or running ALL-TESTS does nothing.
(def-suite all-tests :description "All kvstore tests.")

(def-suite memory-suite :in all-tests :description "In-memory backend.")
(def-suite file-suite   :in all-tests :description "File-backed backend.")
