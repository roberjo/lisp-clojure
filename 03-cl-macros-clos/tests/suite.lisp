(in-package #:edi-dsl/tests)

(def-suite all-tests :description "All edi-dsl tests.")
(in-suite all-tests)

(defun run-all ()
  (run! 'segment-suite)
  (run! 'transaction-suite))
