;;;; Headless test runner. Invoke as:
;;;;   sbcl --non-interactive --load run-tests.lisp
;;;; Requires Quicklisp for FiveAM. Exits 0 on green, 1 on red.

(require :asdf)

#-quicklisp
(let ((qlfile (merge-pathnames "quicklisp/setup.lisp" (user-homedir-pathname))))
  (when (probe-file qlfile) (load qlfile)))

(push (uiop:getcwd) asdf:*central-registry*)

#+quicklisp (ql:quickload :kvstore/tests :silent t)
#-quicklisp (asdf:load-system :kvstore/tests)

(let* ((suite (uiop:find-symbol* :all-tests :kvstore/tests))
       (results (fiveam:run suite)))
  (uiop:quit (if (fiveam:results-status results) 0 1)))
