(in-package #:kvstore/tests)

(in-suite memory-suite)

(test put-then-get
  (let ((s (make-memory-store)))
    (kv-put s "k" 42)
    (is (= 42 (kv-get s "k")))))

(test get-missing-with-default
  (let ((s (make-memory-store)))
    (is (eq :sentinel (kv-get s "absent" :sentinel)))))

(test get-missing-signals
  (let ((s (make-memory-store)))
    (signals missing-key (kv-get s "absent"))))

(test delete-returns-presence
  (let ((s (make-memory-store)))
    (kv-put s "k" 1)
    (is-true  (kv-delete s "k"))
    (is-false (kv-delete s "k"))))

(test count-and-keys
  (let ((s (make-memory-store)))
    (kv-put s "a" 1)
    (kv-put s "b" 2)
    (is (= 2 (kv-count s)))
    (is (equal '("a" "b") (sort (kv-keys s) #'string<)))))

(test clear
  (let ((s (make-memory-store)))
    (kv-put s "a" 1)
    (kv-clear s)
    (is (zerop (kv-count s)))))
