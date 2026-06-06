(in-package #:x12-parser)

;;; Two-pass shape: tokenize into segments, then assemble the envelope tree.
;;; This is intentionally NOT a streaming parser; project README calls streaming
;;; out for a v2.

(defun split-segments (input delims)
  "Return a list of segments. Each segment is a list of strings:
the segment id followed by its element values."
  (let ((seg-sep (delimiters-segment delims))
        (elt-sep (delimiters-element delims)))
    (loop for raw in (split-on input seg-sep)
          for trimmed = (string-trim '(#\Space #\Tab #\Newline #\Return) raw)
          unless (zerop (length trimmed))
          collect (split-on trimmed elt-sep))))

(defun split-on (string ch)
  "Split STRING on character CH. Empty trailing field after a terminator is dropped."
  (loop with start = 0
        with len = (length string)
        for i from 0 below len
        when (char= (char string i) ch)
          collect (subseq string start i) into out
          and do (setf start (1+ i))
        finally
           (return (if (< start len)
                       (append out (list (subseq string start)))
                       out))))

(defun parse-string (input)
  "Parse a complete X12 INPUT string into an X12-INTERCHANGE."
  (let* ((delims (detect-delimiters input))
         (segments (split-segments input delims))
         (interchange (make-instance 'x12-interchange :delimiters delims)))
    (assemble-envelope interchange segments)
    interchange))

(defun assemble-envelope (interchange segments)
  "Walk the flat segment list, building the ISA → GS → ST tree."
  (let ((current-group nil)
        (current-tx nil)
        (current-tx-segments nil))
    (flet ((finalize-tx ()
             (when current-tx
               (setf (transaction-segments current-tx)
                     (nreverse current-tx-segments))
               (push current-tx (functional-group-transactions current-group))
               (setf current-tx nil current-tx-segments nil)))
           (finalize-group ()
             (finalize-tx)
             (when current-group
               (setf (functional-group-transactions current-group)
                     (nreverse (functional-group-transactions current-group)))
               (push current-group (interchange-functional-groups interchange))
               (setf current-group nil))))
      (dolist (seg segments)
        (let ((id (first seg)))
          (cond
            ((string= id "ISA")
             (setf (interchange-header interchange) seg))
            ((string= id "IEA")
             (finalize-group)
             (setf (interchange-trailer interchange) seg)
             (setf (interchange-functional-groups interchange)
                   (nreverse (interchange-functional-groups interchange))))
            ((string= id "GS")
             (finalize-group)
             (setf current-group (make-instance 'functional-group :header seg)))
            ((string= id "GE")
             (finalize-tx)
             (setf (functional-group-trailer current-group) seg)
             (finalize-group))
            ((string= id "ST")
             (finalize-tx)
             (let ((cls (transaction-class-for seg)))
               (setf current-tx (make-instance cls
                                               :control-number (third seg))
                     current-tx-segments (list seg))))
            ((string= id "SE")
             (push seg current-tx-segments)
             (finalize-tx))
            (t
             (if current-tx
                 (push seg current-tx-segments)
                 (error 'parse-error
                        :message (format nil "Segment ~A appeared outside any ST/SE" id))))))))))
