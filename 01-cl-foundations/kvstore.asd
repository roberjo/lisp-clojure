(defsystem "kvstore"
  :description "Minimal key-value store demonstrating ASDF, packages, and CL project structure."
  :version "0.1.0"
  :author "John Roberts"
  :license "MIT"
  :depends-on ()
  :components ((:module "src"
                :serial t
                :components ((:file "package")
                             (:file "backend")
                             (:file "memory")
                             (:file "file")
                             (:file "store"))))
  :in-order-to ((test-op (test-op "kvstore/tests"))))

(defsystem "kvstore/tests"
  :description "Tests for kvstore."
  :depends-on ("kvstore" "fiveam")
  :components ((:module "tests"
                :serial t
                :components ((:file "package")
                             (:file "memory-tests")
                             (:file "file-tests")
                             (:file "suite"))))
  :perform (test-op (op c)
             (uiop:symbol-call :fiveam :run!
                               (uiop:find-symbol* :all-tests :kvstore/tests))))
