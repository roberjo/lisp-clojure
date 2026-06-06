(in-package #:x12-parser/tests)

(def-suite all-tests :description "All x12-parser tests.")
(def-suite delimiters-suite :in all-tests :description "Delimiter detection.")
(def-suite parser-suite     :in all-tests :description "Envelope assembly.")
(def-suite validation-suite :in all-tests :description "Cross-DSL validation.")
(def-suite roundtrip-suite  :in all-tests :description "parse -> write -> parse.")
