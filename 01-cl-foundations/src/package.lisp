(defpackage #:kvstore
  (:use #:cl)
  (:export #:make-memory-store
           #:make-file-store
           #:kv-get
           #:kv-put
           #:kv-delete
           #:kv-keys
           #:kv-count
           #:kv-clear
           #:with-store
           #:store
           #:missing-key))
