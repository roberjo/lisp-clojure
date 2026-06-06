(in-package #:x12-parser/tests)

(in-suite parser-suite)

(defun fixture-path (name)
  (merge-pathnames (format nil "samples/synthetic/~A" name) (uiop:getcwd)))

(test parses-minimal-837d
  (let* ((path (fixture-path "minimal-837d.edi"))
         (ix (parse-file path))
         (group (first (interchange-functional-groups ix)))
         (tx (first (functional-group-transactions group))))
    (is (= 1 (length (interchange-functional-groups ix))))
    (is (= 1 (length (functional-group-transactions group))))
    (is (typep tx 'dental-claim-transaction))
    (is (equal "0001" (transaction-control-number tx)))
    (is-true (find "CLM" (transaction-segments tx)
                   :key #'first :test #'string=))))

(test parses-custom-delimiters
  (let* ((path (fixture-path "custom-delimiters-837d.edi"))
         (ix (parse-file path))
         (group (first (interchange-functional-groups ix)))
         (tx (first (functional-group-transactions group))))
    (is (char= #\| (x12-parser::delimiters-element (x12-parser::interchange-delimiters ix))))
    (is (char= #\^ (x12-parser::delimiters-segment (x12-parser::interchange-delimiters ix))))
    (is (equal "0002" (transaction-control-number tx)))))

(test stray-segment-outside-tx-signals
  (signals x12-parse-error
    (parse-string "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *240601*1200*^*00501*000000001*0*P*:~NM1*41*2*ACME*~IEA*1*000000001~")))
