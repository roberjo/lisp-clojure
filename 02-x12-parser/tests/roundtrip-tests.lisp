(in-package #:x12-parser/tests)

(def-suite roundtrip-suite :description "parse -> write -> compare.")
(in-suite roundtrip-suite)

(test roundtrip-minimal
  (let* ((src-path (merge-pathnames "samples/synthetic/minimal-837d.edi"
                                    (uiop:getcwd)))
         (out-path (merge-pathnames
                    (format nil "rt-~A.edi" (random 1000000))
                    (uiop:temporary-directory))))
    (unwind-protect
         (let ((ix (parse-file src-path)))
           (write-to-file ix out-path)
           ;; Re-parse the output: structure should match.
           (let* ((rt (parse-file out-path))
                  (orig-tx (first (functional-group-transactions
                                   (first (interchange-functional-groups ix)))))
                  (rt-tx (first (functional-group-transactions
                                 (first (interchange-functional-groups rt))))))
             (is (equal (transaction-control-number orig-tx)
                        (transaction-control-number rt-tx)))
             (is (= (length (transaction-segments orig-tx))
                    (length (transaction-segments rt-tx))))))
      (when (probe-file out-path) (delete-file out-path)))))
