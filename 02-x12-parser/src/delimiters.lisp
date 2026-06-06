(in-package #:x12-parser)

;;; The ISA segment is fixed-width and positional. Delimiters are NOT fixed in
;;; X12 — every interchange carries its own. They live at:
;;;
;;;   - element separator   : position  3 (the character immediately after "ISA")
;;;   - sub-element separator: position 104 (the character at byte 104 of the ISA)
;;;   - segment terminator   : position 105 (immediately after sub-elt separator)
;;;
;;; A robust parser MUST read these from the input before splitting anything.

(define-condition parse-error (error)
  ((message :initarg :message :reader parse-error-message)
   (offset  :initarg :offset  :reader parse-error-offset :initform nil))
  (:report (lambda (c s)
             (format s "X12 parse error~@[ at offset ~D~]: ~A"
                     (parse-error-offset c)
                     (parse-error-message c)))))

(defstruct delimiters
  (element     #\* :type character)
  (sub-element #\: :type character)
  (segment     #\~ :type character))

(defun detect-delimiters (input)
  "Inspect the leading ISA segment of INPUT (a string) and extract delimiters.
Signals PARSE-ERROR if INPUT doesn't begin with \"ISA\" or is too short."
  (unless (and (>= (length input) 106)
               (string= "ISA" (subseq input 0 3)))
    (error 'parse-error
           :offset 0
           :message "Input does not begin with an ISA segment"))
  (make-delimiters :element     (char input 3)
                   :sub-element (char input 104)
                   :segment     (char input 105)))
