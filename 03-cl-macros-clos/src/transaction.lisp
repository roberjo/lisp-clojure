(in-package #:edi-dsl)

;;; CLOS hierarchy for X12 transaction sets.
;;;
;;; TRANSACTION is the root; concrete subclasses (CLAIM-TRANSACTION, etc.) carry
;;; transaction-set-specific behavior via method specialization. Method
;;; combination is used in two places:
;;;
;;;   - VALIDATE has :around methods that collect errors from all applicable
;;;     methods (the AUDITABLE mixin demonstrates this).
;;;   - TRANSFORM-TO uses standard dispatch on (source-class, target-keyword).

(defclass transaction ()
  ((segments       :initarg :segments       :accessor transaction-segments
                   :initform nil)
   (control-number :initarg :control-number :accessor transaction-control-number
                   :initform nil)))

(defclass auditable () ()
  (:documentation "Mixin: VALIDATE methods specialized on AUDITABLE attach
control-number presence checks via method combination."))

(defclass claim-transaction (transaction auditable) ())
(defclass dental-claim-transaction (claim-transaction) ())
(defclass remittance-transaction (transaction auditable) ())

;;; Generics

(defgeneric validate (tx)
  (:documentation "Return a list of VALIDATION-ERRORs. Empty list = valid.")
  (:method-combination append))

(defgeneric serialize (tx &key delimiters)
  (:documentation "Serialize a transaction back to X12 wire format."))

(defgeneric transform-to (tx target-keyword)
  (:documentation "Convert a transaction to a different representation, e.g. :plist."))
