(in-package #:kvstore)

(defclass file-store (memory-store)
  ((path :initarg :path :reader file-store-path))
  (:documentation "File-backed store. Loads the file on construction, writes the whole table on every mutation.
Trade-off: simple and correct; not appropriate for large datasets. Format is a single READable s-expression — an alist."))

(defun make-file-store (path)
  (let ((s (make-instance 'file-store :path path)))
    (load-from-file s)
    s))

(defun load-from-file (s)
  (let ((path (file-store-path s)))
    (when (probe-file path)
      (with-open-file (in path :direction :input)
        (let ((data (read in nil nil)))
          (dolist (pair data)
            (setf (gethash (car pair) (memory-table s)) (cdr pair))))))))

(defun save-to-file (s)
  (let ((path (file-store-path s))
        (pairs (loop for k being the hash-keys of (memory-table s)
                       using (hash-value v)
                     collect (cons k v))))
    (with-open-file (out path :direction :output
                              :if-exists :supersede
                              :if-does-not-exist :create)
      (with-standard-io-syntax
        (let ((*print-readably* t))
          (prin1 pairs out)
          (terpri out))))))

(defmethod kv-put :after ((s file-store) key value)
  (declare (ignore key value))
  (save-to-file s))

(defmethod kv-delete :after ((s file-store) key)
  (declare (ignore key))
  (save-to-file s))

(defmethod kv-clear :after ((s file-store))
  (save-to-file s))
