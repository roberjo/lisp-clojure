(require :asdf)
#-quicklisp
(let ((qlfile (merge-pathnames "quicklisp/setup.lisp" (user-homedir-pathname))))
  (when (probe-file qlfile) (load qlfile)))
;; Make sibling project 03 (edi-dsl) discoverable.
(push (uiop:getcwd) asdf:*central-registry*)
(push (merge-pathnames "../03-cl-macros-clos/" (uiop:getcwd))
      asdf:*central-registry*)
#+quicklisp (ql:quickload :x12-parser/tests :silent t)
#-quicklisp (asdf:load-system :x12-parser/tests)
(let* ((suite (uiop:find-symbol* :all-tests :x12-parser/tests))
       (results (fiveam:run suite)))
  (uiop:quit (if (fiveam:results-status results) 0 1)))
