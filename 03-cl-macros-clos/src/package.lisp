(defpackage #:edi-dsl
  (:use #:cl)
  (:export ;; segment DSL
           #:define-segment
           #:segment-definition
           #:find-segment-def
           #:segment-def-id
           #:segment-def-name
           #:segment-def-elements
           #:element-def
           #:element-def-position
           #:element-def-name
           #:element-def-required-p
           #:element-def-type
           ;; CLOS transaction hierarchy
           #:transaction
           #:claim-transaction
           #:dental-claim-transaction
           #:remittance-transaction
           #:transaction-segments
           #:transaction-control-number
           #:validate
           #:serialize
           #:transform-to
           ;; validation errors
           #:validation-error
           #:make-validation-error
           #:validation-error-segment-id
           #:validation-error-element-position
           #:validation-error-loop
           #:validation-error-message))
