;;;; Helper: parse an EDI file and emit each transaction as a plist
;;;; to stdout. Used by project 04 to generate fixture EDN.
;;;;
;;;; Usage:
;;;;   sbcl --script bin/emit-plist.lisp samples/synthetic/minimal-837d.edi

(require :asdf)
#-quicklisp
(let ((qlfile (merge-pathnames "quicklisp/setup.lisp" (user-homedir-pathname))))
  (when (probe-file qlfile) (load qlfile)))
(push (uiop:getcwd) asdf:*central-registry*)
(push (merge-pathnames "../03-cl-macros-clos/" (uiop:getcwd))
      asdf:*central-registry*)
#+quicklisp (ql:quickload :x12-parser :silent t)
#-quicklisp (asdf:load-system :x12-parser)

(let ((path (or (first uiop:*command-line-arguments*)
                (error "Usage: emit-plist.lisp <edi-file>"))))
  (let ((ix (x12-parser:parse-file path)))
    (dolist (group (x12-parser:interchange-functional-groups ix))
      (dolist (tx (x12-parser:functional-group-transactions group))
        (with-standard-io-syntax
          (let ((*print-readably* t))
            (prin1 (edi-dsl:transform-to tx :plist))
            (terpri)))))))
