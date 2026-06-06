(defsystem "x12-parser"
  :description "X12 healthcare EDI parser focused on the 837D (dental) transaction set."
  :version "0.1.0"
  :author "John Roberts"
  :license "MIT"
  :depends-on ("edi-dsl")
  :components ((:module "src"
                :serial t
                :components ((:file "package")
                             (:file "delimiters")
                             (:file "segments-837d")
                             (:file "envelope")
                             (:file "parser")
                             (:file "io"))))
  :in-order-to ((test-op (test-op "x12-parser/tests"))))

(defsystem "x12-parser/tests"
  :depends-on ("x12-parser" "fiveam")
  :components ((:module "tests"
                :serial t
                :components ((:file "package")
                             (:file "delimiters-tests")
                             (:file "parser-tests")
                             (:file "validation-tests")
                             (:file "roundtrip-tests")
                             (:file "suite"))))
  :perform (test-op (op c)
             (uiop:symbol-call :fiveam :run!
                               (uiop:find-symbol* :all-tests :x12-parser/tests))))
