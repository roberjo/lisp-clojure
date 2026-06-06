(in-package #:edi-dsl)

;;; APPEND method combination: every applicable VALIDATE method contributes
;;; its errors, and the framework concatenates them. No method needs to know
;;; about the others; new mixins (e.g. a SOX-AUDITABLE mixin) plug in without
;;; touching existing code.

(defmethod validate append ((tx transaction))
  (let ((errors '()))
    (dolist (seg (transaction-segments tx))
      (let* ((id (first seg))
             (def (find-segment-def id)))
        (cond
          ((null def)
           (push (make-validation-error :segment-id id
                                        :message (format nil "Unknown segment id ~A" id))
                 errors))
          (t
           (dolist (elt-def (segment-def-elements def))
             (when (element-def-required-p elt-def)
               (let* ((pos (element-def-position elt-def))
                      ;; seg = (id elt1 elt2 ...), so position N is at (nth N seg).
                      (val (nth pos seg)))
                 (when (or (null val) (and (stringp val) (zerop (length val))))
                   (push (make-validation-error
                          :segment-id id
                          :element-position pos
                          :message (format nil "Required element ~A (~A) missing"
                                           pos (element-def-name elt-def)))
                         errors)))))))))
    (nreverse errors)))

(defmethod validate append ((tx auditable))
  (unless (transaction-control-number tx)
    (list (make-validation-error
           :segment-id "ST"
           :message "Missing transaction control number"))))

(defmethod validate append ((tx dental-claim-transaction))
  ;; 837D-specific: at least one CLM segment is required.
  (unless (find "CLM" (transaction-segments tx) :test #'string= :key #'first)
    (list (make-validation-error
           :segment-id "CLM"
           :loop "2300"
           :message "837D requires at least one CLM (claim) segment"))))

;;; SERIALIZE — simple wire-format emission. Defaults: '*' element, '~' segment.

(defmethod serialize ((tx transaction) &key (delimiters '(:element #\* :segment #\~)))
  (let ((elt-sep (getf delimiters :element))
        (seg-sep (getf delimiters :segment)))
    (with-output-to-string (s)
      (dolist (seg (transaction-segments tx))
        (format s "~A" (first seg))
        (dolist (elt (rest seg))
          (format s "~C~A" elt-sep (or elt "")))
        (format s "~C" seg-sep)))))

;;; TRANSFORM-TO — to plist, the lingua franca for project 04.

(defmethod transform-to ((tx transaction) (target (eql :plist)))
  (list :type (class-name (class-of tx))
        :control-number (transaction-control-number tx)
        :segments (mapcar (lambda (seg)
                            (list :id (first seg)
                                  :elements (rest seg)))
                          (transaction-segments tx))))
