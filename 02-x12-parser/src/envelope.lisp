(in-package #:x12-parser)

;;; Internal model for the envelope hierarchy:
;;;   X12-INTERCHANGE → list of FUNCTIONAL-GROUPs → list of TRANSACTION-SETs

(defclass x12-interchange ()
  ((header :initarg :header :accessor interchange-header)
   (trailer :initarg :trailer :accessor interchange-trailer :initform nil)
   (functional-groups :initarg :functional-groups
                      :accessor interchange-functional-groups
                      :initform nil)
   (delimiters :initarg :delimiters :accessor interchange-delimiters)))

(defclass functional-group ()
  ((header :initarg :header :accessor functional-group-header)
   (trailer :initarg :trailer :accessor functional-group-trailer :initform nil)
   (transactions :initarg :transactions
                 :accessor functional-group-transactions
                 :initform nil)))

(defun transaction-class-for (st-segment)
  "Pick a CLOS class based on the ST01 transaction-set id. ST01 = '837' is a
claim; we further specialize 837D as the dental variant via the GS08 (industry
identifier) check upstream."
  ;; st-segment = ("ST" "837" "0001" ...)
  (cond ((string= "837" (second st-segment)) 'dental-claim-transaction)
        ((string= "835" (second st-segment)) 'remittance-transaction)
        (t 'transaction)))
