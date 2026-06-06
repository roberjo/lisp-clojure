(in-package #:x12-parser/tests)

(def-suite delimiters-suite :description "Delimiter detection from the ISA segment.")
(in-suite delimiters-suite)

(test detects-default-delimiters
  (let* ((isa (format nil "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *240601*1200*^*00501*000000001*0*P*:~~"))
         (d   (detect-delimiters isa)))
    (is (char= #\* (x12-parser::delimiters-element d)))
    (is (char= #\: (x12-parser::delimiters-sub-element d)))
    (is (char= #\~ (x12-parser::delimiters-segment d)))))

(test rejects-non-isa-input
  (signals parse-error (detect-delimiters "GS*HC*X*Y*20240601*1200*1*X*005010X224A2~")))
