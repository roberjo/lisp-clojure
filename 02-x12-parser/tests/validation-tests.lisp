(in-package #:x12-parser/tests)

(in-suite validation-suite)

(test parsed-fixture-validates-clean
  (let* ((ix (parse-file (merge-pathnames "samples/synthetic/minimal-837d.edi"
                                          (uiop:getcwd))))
         (tx (first (functional-group-transactions
                     (first (interchange-functional-groups ix)))))
         (errors (validate tx)))
    (is (null errors)
        "Expected no validation errors, got: ~S" errors)))

(test missing-clm-flagged
  ;; Hand-build a dental claim with no CLM segment; expect validate to flag it.
  (let ((tx (make-instance 'dental-claim-transaction
                           :control-number "0001"
                           :segments '(("NM1" "85" "2" "ACME DENTAL")))))
    (let ((errs (validate tx)))
      (is-true (find "CLM" errs
                     :key #'validation-error-segment-id
                     :test #'string=)))))
