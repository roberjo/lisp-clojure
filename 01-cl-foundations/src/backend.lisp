(in-package #:kvstore)

(defclass store () ()
  (:documentation "Abstract store. Backends implement the kv-* generics."))

(define-condition missing-key (error)
  ((key :initarg :key :reader missing-key-key))
  (:report (lambda (c s)
             (format s "No value for key ~S" (missing-key-key c)))))

(defgeneric kv-get (store key &optional default)
  (:documentation "Return value for KEY, or DEFAULT if absent. Signals MISSING-KEY when DEFAULT is unsupplied and the key is missing."))

(defgeneric kv-put (store key value)
  (:documentation "Store VALUE under KEY. Returns VALUE."))

(defgeneric kv-delete (store key)
  (:documentation "Remove KEY. Returns T if present, NIL otherwise."))

(defgeneric kv-keys (store)
  (:documentation "Return a fresh list of keys, order unspecified."))

(defgeneric kv-count (store)
  (:documentation "Number of entries."))

(defgeneric kv-clear (store)
  (:documentation "Remove all entries. Returns STORE."))

(defmethod kv-count (store)
  (length (kv-keys store)))

(defmacro with-store ((var store) &body body)
  "Evaluate BODY with VAR bound to STORE. Provided as a hook for backends that need open/close semantics later."
  `(let ((,var ,store))
     ,@body))
