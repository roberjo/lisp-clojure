(in-package #:edi-dsl/tests)

(in-suite transaction-suite)

;;; Define a couple of segments the tests can rely on.
(define-segment "CLM" (:name :claim)
  (1 :claim-id :required t)
  (2 :total-charge :required t))

(define-segment "NM1" (:name :name)
  (1 :entity-id-code :required t)
  (2 :entity-type-qualifier :required t)
  (3 :name :required t))

(test valid-dental-claim-has-no-errors
  (let ((tx (make-instance 'dental-claim-transaction
                           :control-number "0001"
                           :segments '(("CLM" "C12345" "250.00")
                                       ("NM1" "85" "2" "ACME DENTAL")))))
    (is (null (validate tx)))))

(test missing-clm-on-dental-claim
  (let ((tx (make-instance 'dental-claim-transaction
                           :control-number "0001"
                           :segments '(("NM1" "85" "2" "ACME DENTAL")))))
    (let ((errs (validate tx)))
      (is-true (find "CLM" errs :key #'validation-error-segment-id :test #'string=)))))

(test missing-control-number-on-auditable
  (let ((tx (make-instance 'dental-claim-transaction
                           :control-number nil
                           :segments '(("CLM" "C1" "10.00")))))
    (is-true (find "ST" (validate tx)
                   :key #'validation-error-segment-id :test #'string=))))

(test missing-required-element
  (let ((tx (make-instance 'dental-claim-transaction
                           :control-number "0001"
                           :segments '(("CLM" "C1" nil))))) ; total-charge missing
    (let ((errs (validate tx)))
      (is-true (find-if (lambda (e)
                          (and (string= "CLM" (validation-error-segment-id e))
                               (eql 2 (validation-error-element-position e))))
                        errs)))))

(test serialize-roundtrip-shape
  (let* ((tx (make-instance 'dental-claim-transaction
                            :control-number "0001"
                            :segments '(("CLM" "C1" "10.00")
                                        ("NM1" "85" "2" "ACME"))))
         (s (serialize tx)))
    (is (search "CLM*C1*10.00~" s))
    (is (search "NM1*85*2*ACME~" s))))

(test transform-to-plist
  (let* ((tx (make-instance 'dental-claim-transaction
                            :control-number "0001"
                            :segments '(("CLM" "C1" "10.00"))))
         (pl (transform-to tx :plist)))
    (is (eq :dental-claim-transaction (getf pl :type)))
    (is (equal "0001" (getf pl :control-number)))))
