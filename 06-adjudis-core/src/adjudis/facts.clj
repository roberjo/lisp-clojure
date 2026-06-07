(ns adjudis.facts
  "Clara fact types. Records (not maps) because Clara's pattern matching keys
   on type — using maps everywhere would make the LHS of every rule a manual
   :as binding plus a :test predicate.")

;; ──────────────────────────────────────────────────────────────────────────
;; Input facts (inserted at the start of every adjudication)
;; ──────────────────────────────────────────────────────────────────────────

(defrecord Claim
    [claim-id              ;; "PCN001"
     control-number        ;; "0001"
     subscriber-id         ;; "M00112233"
     billing-provider      ;; "ACME DENTAL"
     network               ;; :in-network | :out-of-network
     total-charge          ;; 250.0
     pre-auth-reference    ;; nil or "AUTH123"
     transaction-type])    ;; :dental

(defrecord ServiceLine
    [claim-id              ;; back-reference
     line-number           ;; 1
     procedure-code        ;; "D1110"
     service-date          ;; "2024-05-15" (ISO date string)
     charge                ;; 250.0
     units])               ;; 1

(defrecord Member
    [subscriber-id         ;; "M00112233"
     date-of-birth         ;; "1985-03-12"
     coverage-start        ;; "2024-01-01"
     coverage-end          ;; "2024-12-31" or nil for open-ended
     plan-type])           ;; :ppo | :hmo

;; HistoricalLine: prior adjudicated line for the same member. Loaded from the
;; history store so frequency / annual-max rules can see beyond the current claim.
(defrecord HistoricalLine
    [subscriber-id
     procedure-code
     service-date
     allowed-amount])

;; CatalogRule: one rule loaded from resources/rule-catalog/. The engine inserts
;; every rule as a fact; the productions filter by :category.
(defrecord CatalogRule
    [rule-id
     category
     description
     severity
     reason-code
     params
     citation])

;; ──────────────────────────────────────────────────────────────────────────
;; Output facts (emitted by rules)
;; ──────────────────────────────────────────────────────────────────────────

;; Finding: one fired rule, with context the explanation engine can render.
;; extra-context is category-specific.
(defrecord Finding
    [rule-id
     rule-category
     severity              ;; :deny | :pending | :adjust | :warn | :inform
     reason-code
     message
     affected-lines        ;; vector of line-number
     citation
     extra-context])
