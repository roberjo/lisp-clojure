(in-package #:kvstore/tests)

(def-suite file-suite :description "File-backed backend tests.")
(in-suite file-suite)

(defun temp-path ()
  (merge-pathnames (format nil "kvstore-test-~A.sexp" (random 1000000))
                   (uiop:temporary-directory)))

(test file-roundtrip
  (let ((path (temp-path)))
    (unwind-protect
         (progn
           (let ((s (make-file-store path)))
             (kv-put s "name" "ada")
             (kv-put s "n"    7))
           ;; Reopen: data should be present.
           (let ((s2 (make-file-store path)))
             (is (equal "ada" (kv-get s2 "name")))
             (is (= 7        (kv-get s2 "n")))))
      (when (probe-file path) (delete-file path)))))

(test file-delete-persists
  (let ((path (temp-path)))
    (unwind-protect
         (progn
           (let ((s (make-file-store path)))
             (kv-put s "k" 1)
             (kv-delete s "k"))
           (let ((s2 (make-file-store path)))
             (is (zerop (kv-count s2)))))
      (when (probe-file path) (delete-file path)))))
