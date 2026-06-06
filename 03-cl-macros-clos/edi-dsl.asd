(defsystem "edi-dsl"
  :description "Macro DSL (define-segment) + CLOS transaction hierarchy. Used by the X12 parser in 02."
  :version "0.1.0"
  :author "John Roberts"
  :license "MIT"
  :depends-on ()
  :components ((:module "src"
                :serial t
                :components ((:file "package")
                             (:file "registry")
                             (:file "segment-def")
                             (:file "transaction")
                             (:file "validate"))))
  :in-order-to ((test-op (test-op "edi-dsl/tests"))))

(defsystem "edi-dsl/tests"
  :depends-on ("edi-dsl" "fiveam")
  :components ((:module "tests"
                :serial t
                :components ((:file "package")
                             (:file "segment-tests")
                             (:file "transaction-tests")
                             (:file "suite"))))
  :perform (test-op (op c)
             (uiop:symbol-call :fiveam :run!
                               (uiop:find-symbol* :all-tests :edi-dsl/tests))))
