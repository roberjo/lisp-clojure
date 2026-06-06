(in-package #:edi-dsl/tests)

(def-suite segment-suite :description "DEFINE-SEGMENT macro and registry.")
(in-suite segment-suite)

;;; The expansion test: prove the macro generates the shape we expect WITHOUT
;;; executing it. A senior CL test should cover macro expansion, not only
;;; runtime behavior.

(test define-segment-expansion-is-hygienic
  (let ((expansion (macroexpand-1
                    '(define-segment "ZZZ" (:name :test)
                      (1 :elt-one :required t)
                      (2 :elt-two)))))
    ;; Outer form is a PROGN with a LET binding a GENSYMmed variable.
    (is (eq 'progn (first expansion)))
    (let ((let-form (second expansion)))
      (is (eq 'let (first let-form)))
      ;; The bound variable is a gensym (uninterned).
      (let ((bound-var (caar (second let-form))))
        (is-true (and (symbolp bound-var)
                      (null (symbol-package bound-var))))))))

(test define-segment-registers
  (eval '(define-segment "ZZZ" (:name :test)
          (1 :first-element :required t)
          (2 :second-element)))
  (let ((def (find-segment-def "ZZZ")))
    (is-true def)
    (is (string= "ZZZ" (segment-def-id def)))
    (is (= 2 (length (segment-def-elements def))))
    (is-true  (element-def-required-p (first (segment-def-elements def))))
    (is-false (element-def-required-p (second (segment-def-elements def))))))

(test unknown-segment-returns-nil
  (is (null (find-segment-def "DOES-NOT-EXIST"))))
