(ns adjudis.schema
  "Decision / finding / catalog-rule shapes. Documentation in code, not
   enforcement — Phase 2 will introduce Malli schemas when the API is added.

   Centralized so every namespace agrees on field names.")

;; ──────────────────────────────────────────────────────────────────────────
;; Input claim shape (mirrors project 04's NormalizedClaim output)
;; ──────────────────────────────────────────────────────────────────────────
;;
;; {:transaction-type   :dental
;;  :control-number     "0001"
;;  :claim-id           "PCN001"
;;  :total-charge       250.0
;;  :billing-provider   "ACME DENTAL"
;;  :subscriber         {:last "DOE" :first "JANE" :member-id "M00112233"}
;;  :service-lines      [{:line-number 1
;;                        :procedure-code "D1110"
;;                        :charge 250.0
;;                        :service-date "2024-05-15"
;;                        :units 1}]}

;; ──────────────────────────────────────────────────────────────────────────
;; Output decision shape
;; ──────────────────────────────────────────────────────────────────────────
;;
;; {:claim-id          "PCN001"
;;  :verdict           :paid | :denied | :pending
;;  :patient-responsibility 42.00
;;  :provider-payment       158.00
;;  :total-allowed          200.00
;;  :reason-codes      ["CO-45" ...]
;;  :line-decisions    [{:line-number 1
;;                       :verdict :paid
;;                       :allowed 158.00
;;                       :patient-responsibility 42.00
;;                       :reason-codes []}]
;;  :findings          [<finding>...]            ; full provenance
;;  :rule-versions     {:catalog-version "2024-Q2"
;;                      :engine-version  "0.1.0"}}

;; ──────────────────────────────────────────────────────────────────────────
;; Finding shape (one per rule that fired)
;; ──────────────────────────────────────────────────────────────────────────
;;
;; {:rule-id        "DENTAL-PROPHY-FREQUENCY"
;;  :rule-category  :frequency-limit
;;  :severity       :deny | :warn | :info | :inform | :adjust
;;  :reason-code    "FREQ-PROPHY"     ; payer-facing code
;;  :message        "Adult prophylaxis exceeded 2/year limit"
;;  :affected-lines [1 3]
;;  :citation       {:source "Plan handbook section 4.2" :url "..."}}

;; ──────────────────────────────────────────────────────────────────────────
;; Catalog rule shape (EDN files under resources/rule-catalog/*.edn)
;; ──────────────────────────────────────────────────────────────────────────
;;
;; {:rule-id     "DENTAL-PROPHY-FREQUENCY"
;;  :category    :frequency-limit
;;  :description "Adult prophylaxis limited to 2 per benefit year"
;;  :severity    :deny
;;  :reason-code "FREQ-PROPHY"
;;  :params      {:procedure-codes #{"D1110"}
;;                :max-per-year    2}
;;  :citation    {:source "Plan handbook section 4.2"}}

(def severity-rank
  "For combining multiple findings into a single verdict: deny > pending > adjust > warn > inform."
  {:deny    4
   :pending 3
   :adjust  2
   :warn    1
   :inform  0})

(def catalog-version
  "Currently-loaded rule library version. Phase 2 will derive this from a git tag
   or a deliberate manifest; for now it's a constant."
  "2024-Q2-mvp")

(def engine-version "0.1.0")
