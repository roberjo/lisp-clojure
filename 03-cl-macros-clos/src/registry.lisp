(in-package #:edi-dsl)

(defvar *segment-registry* (make-hash-table :test 'equal)
  "Maps segment id (string, e.g. \"NM1\") to a SEGMENT-DEFINITION instance.
Populated at compile time by DEFINE-SEGMENT.")

(defun register-segment (def)
  (setf (gethash (segment-def-id def) *segment-registry*) def))

(defun find-segment-def (id)
  "Look up a segment definition by its X12 id. Returns NIL if unknown."
  (gethash id *segment-registry*))
