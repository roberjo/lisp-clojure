(in-package #:edi-dsl/tests)

(def-suite all-tests :description "All edi-dsl tests.")
(def-suite segment-suite     :in all-tests :description "DEFINE-SEGMENT + registry.")
(def-suite transaction-suite :in all-tests :description "CLOS hierarchy + APPEND VALIDATE.")
