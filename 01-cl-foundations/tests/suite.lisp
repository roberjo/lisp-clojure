(in-package #:kvstore/tests)

(def-suite all-tests :description "All kvstore tests.")
(in-suite all-tests)

(defun run-all ()
  (run! 'memory-suite)
  (run! 'file-suite))
