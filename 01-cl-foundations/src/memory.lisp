(in-package #:kvstore)

(defclass memory-store (store)
  ((table :initform (make-hash-table :test 'equal) :reader memory-table)))

(defun make-memory-store () (make-instance 'memory-store))

(defmethod kv-get ((s memory-store) key &optional (default nil default-supplied-p))
  (multiple-value-bind (val present) (gethash key (memory-table s))
    (cond (present val)
          (default-supplied-p default)
          (t (error 'missing-key :key key)))))

(defmethod kv-put ((s memory-store) key value)
  (setf (gethash key (memory-table s)) value)
  value)

(defmethod kv-delete ((s memory-store) key)
  (remhash key (memory-table s)))

(defmethod kv-keys ((s memory-store))
  (loop for k being the hash-keys of (memory-table s) collect k))

(defmethod kv-count ((s memory-store))
  (hash-table-count (memory-table s)))

(defmethod kv-clear ((s memory-store))
  (clrhash (memory-table s))
  s)
