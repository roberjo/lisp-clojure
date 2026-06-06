(in-package #:x12-parser/tests)

(def-suite all-tests :description "All x12-parser tests.")
(in-suite all-tests)

(defun run-all ()
  (run! 'delimiters-suite)
  (run! 'parser-suite)
  (run! 'validation-suite)
  (run! 'roundtrip-suite))
