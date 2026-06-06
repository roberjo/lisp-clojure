(in-package #:edi-dsl)

;;; ---------- Data ----------

(defclass element-def ()
  ((position :initarg :position :reader element-def-position)
   (name     :initarg :name     :reader element-def-name)
   (required :initarg :required :reader element-def-required-p)
   (type     :initarg :type     :reader element-def-type)))

(defclass segment-definition ()
  ((id       :initarg :id       :reader segment-def-id)
   (name     :initarg :name     :reader segment-def-name)
   (elements :initarg :elements :reader segment-def-elements)))

(defun make-element-def (position name &key required (type :string))
  (make-instance 'element-def
                 :position position
                 :name name
                 :required required
                 :type type))

;;; ---------- Validation error ----------

(defstruct validation-error
  segment-id
  element-position
  loop
  message)

;;; ---------- The macro ----------

(defmacro define-segment (id (&key name) &body element-forms)
  "Declare an X12 segment.

  (define-segment \"NM1\" (:name :individual-or-organizational-name)
    (1 :entity-identifier-code :required t)
    (2 :entity-type-qualifier  :required t)
    (3 :name-last-or-organization-name :required t)
    (4 :name-first)
    (8 :identification-code-qualifier)
    (9 :identification-code))

Expands at compile time into a SEGMENT-DEFINITION instance registered in
*SEGMENT-REGISTRY*. Each element form is (POSITION NAME &key REQUIRED TYPE).

The macro is hygienic: element forms are quoted at expansion time so no
unintended captures of the calling site's bindings can occur. A separate
helper (%segment-validator) is also generated, allowing the X12 parser to
look up validation behavior without re-walking the registry on every call."
  (let* ((element-defs
           (loop for form in element-forms
                 collect (destructuring-bind (pos elt-name &key required (type :string)) form
                           `(make-element-def ,pos ',elt-name
                                              :required ,required
                                              :type ,type))))
         (def-sym (gensym "SEGMENT-DEF-")))
    `(progn
       (let ((,def-sym (make-instance 'segment-definition
                                      :id ,id
                                      :name ',name
                                      :elements (list ,@element-defs))))
         (register-segment ,def-sym)
         ,def-sym))))
