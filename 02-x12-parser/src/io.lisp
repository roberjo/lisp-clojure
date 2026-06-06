(in-package #:x12-parser)

(defun read-file-string (path)
  (with-open-file (in path :direction :input)
    (with-output-to-string (out)
      (loop for line = (read-line in nil nil)
            while line do (write-string line out)))))

(defun parse-file (path)
  (parse-string (read-file-string path)))

(defun write-to-file (interchange path)
  "Re-serialize INTERCHANGE to PATH using its original delimiters."
  (let ((delims (interchange-delimiters interchange)))
    (with-open-file (out path :direction :output
                              :if-exists :supersede
                              :if-does-not-exist :create)
      (emit-segment out (interchange-header interchange) delims)
      (dolist (group (interchange-functional-groups interchange))
        (emit-segment out (functional-group-header group) delims)
        (dolist (tx (functional-group-transactions group))
          (dolist (seg (transaction-segments tx))
            (emit-segment out seg delims)))
        (when (functional-group-trailer group)
          (emit-segment out (functional-group-trailer group) delims)))
      (when (interchange-trailer interchange)
        (emit-segment out (interchange-trailer interchange) delims)))))

(defun emit-segment (stream segment delims)
  (let ((elt (delimiters-element delims))
        (seg (delimiters-segment delims)))
    (format stream "~A" (first segment))
    (dolist (e (rest segment))
      (format stream "~C~A" elt (or e "")))
    (format stream "~C" seg)))
