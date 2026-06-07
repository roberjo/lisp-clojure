(ns adjudis.rules
  "Clara productions, one per rule category. Each production walks the
   in-session facts (CatalogRule, ServiceLine, Member, HistoricalLine, Claim)
   and inserts Finding facts for any violation.

   Architectural punchline: productions are CODE (one per category, fixed in
   number); rule INSTANCES are DATA (resources/rule-catalog/*.edn). New rules
   in an existing category cost nothing; new categories are a new defrule."
  (:require [clara.rules :refer [defrule defquery insert!]]
            [clara.rules.accumulators :as acc]
            [adjudis.facts :as f]
            [adjudis.dates :as d])
  (:import  [adjudis.facts Claim ServiceLine Member HistoricalLine CatalogRule Finding]))

;; ──────────────────────────────────────────────────────────────────────────
;; Helpers (Clojure functions used from rule RHS)
;; ──────────────────────────────────────────────────────────────────────────

(defn- count-occurrences-in-year
  "How many times does proc-code appear, in current-lines + history, in the
   benefit year containing reference-date?"
  [proc-codes current-lines history reference-date]
  (let [match? #(contains? proc-codes (:procedure-code %))]
    (+ (count (filter #(and (match? %)
                            (d/same-benefit-year? (:service-date %) reference-date))
                      current-lines))
       (count (filter #(and (match? %)
                            (d/same-benefit-year? (:service-date %) reference-date))
                      history)))))

(defn- affected-lines [lines proc-codes]
  (mapv :line-number
        (filter #(contains? proc-codes (:procedure-code %)) lines)))

;; ──────────────────────────────────────────────────────────────────────────
;; Productions
;; ──────────────────────────────────────────────────────────────────────────

(defrule frequency-limit-violation
  "For each :frequency-limit catalog rule, count matching current+historical
   lines in the benefit year of any matching service line; emit a Finding if
   the count exceeds the rule's max-per-year."
  [?rule <- CatalogRule (= category :frequency-limit) (= ?params params)]
  [?line <- ServiceLine (contains? (:procedure-codes ?params) procedure-code)]
  [?cur-lines  <- (acc/all) :from [ServiceLine]]
  [?hist-lines <- (acc/all) :from [HistoricalLine]]
  =>
  (let [proc-codes (:procedure-codes ?params)
        max-per-yr (:max-per-year   ?params)
        occurrences (count-occurrences-in-year proc-codes ?cur-lines ?hist-lines (:service-date ?line))]
    (when (> occurrences max-per-yr)
      (insert!
       (f/->Finding (:rule-id ?rule)
                  :frequency-limit
                  (:severity ?rule)
                  (:reason-code ?rule)
                  (str (:description ?rule)
                       " — observed " occurrences " occurrences (limit: " max-per-yr ")")
                  (affected-lines ?cur-lines proc-codes)
                  (:citation ?rule)
                  {:observed-count occurrences
                   :limit          max-per-yr})))))

(defrule age-appropriate-max-violation
  "For each :age-appropriate rule with :max-age, deny if member's age on the
   service date exceeds max-age."
  [?rule   <- CatalogRule (= category :age-appropriate)
                          (contains? params :max-age)
                          (= ?params params)]
  [?line   <- ServiceLine (contains? (:procedure-codes ?params) procedure-code)
                          (= ?dos service-date)]
  [?member <- Member]
  =>
  (let [age (d/age-on (:date-of-birth ?member) ?dos)]
    (when (and age (> age (:max-age ?params)))
      (insert!
       (f/->Finding (:rule-id ?rule)
                  :age-appropriate
                  (:severity ?rule)
                  (:reason-code ?rule)
                  (str (:description ?rule)
                       " — member age " age " exceeds max-age " (:max-age ?params))
                  [(:line-number ?line)]
                  (:citation ?rule)
                  {:member-age age :max-age (:max-age ?params)})))))

(defrule age-appropriate-min-violation
  "For each :age-appropriate rule with :min-age, deny if member's age on the
   service date is below min-age."
  [?rule   <- CatalogRule (= category :age-appropriate)
                          (contains? params :min-age)
                          (= ?params params)]
  [?line   <- ServiceLine (contains? (:procedure-codes ?params) procedure-code)
                          (= ?dos service-date)]
  [?member <- Member]
  =>
  (let [age (d/age-on (:date-of-birth ?member) ?dos)]
    (when (and age (< age (:min-age ?params)))
      (insert!
       (f/->Finding (:rule-id ?rule)
                  :age-appropriate
                  (:severity ?rule)
                  (:reason-code ?rule)
                  (str (:description ?rule)
                       " — member age " age " is below min-age " (:min-age ?params))
                  [(:line-number ?line)]
                  (:citation ?rule)
                  {:member-age age :min-age (:min-age ?params)})))))

(defrule pre-auth-missing
  "Pend any line above the rule's threshold-amount whose claim has no
   pre-auth-reference."
  [?rule  <- CatalogRule (= category :pre-auth-required) (= ?params params)]
  [?claim <- Claim (nil? pre-auth-reference)]
  [?line  <- ServiceLine (contains? (:procedure-codes ?params) procedure-code)
                          (>= charge (:threshold-amount ?params))]
  =>
  (insert!
   (f/->Finding (:rule-id ?rule)
              :pre-auth-required
              (:severity ?rule)
              (:reason-code ?rule)
              (str (:description ?rule)
                   " — line " (:line-number ?line) " charged $" (:charge ?line)
                   " with no auth on file")
              [(:line-number ?line)]
              (:citation ?rule)
              {:threshold (:threshold-amount ?params)
               :charge    (:charge ?line)})))

(defrule eligibility-inactive
  "Deny any line whose service date falls outside the member's coverage window."
  [?rule   <- CatalogRule (= category :eligibility)]
  [?member <- Member (= ?start coverage-start) (= ?end coverage-end)]
  [?line   <- ServiceLine (not (d/within? service-date ?start ?end))]
  =>
  (insert!
   (f/->Finding (:rule-id ?rule)
              :eligibility
              (:severity ?rule)
              (:reason-code ?rule)
              (str "Service date " (:service-date ?line)
                   " is outside coverage window ["
                   (or ?start "-∞") ", " (or ?end "+∞") "]")
              [(:line-number ?line)]
              (:citation ?rule)
              {:service-date  (:service-date ?line)
               :coverage-start ?start
               :coverage-end   ?end})))

(defrule annual-max-exceeded
  "If the sum of (this claim's charges) + historical allowed amounts in the
   benefit year exceeds the rule's :max-amount, pend the claim with the
   remaining benefit recorded."
  [?rule <- CatalogRule (= category :annual-maximum) (= ?params params)]
  [?cur-lines  <- (acc/all) :from [ServiceLine]]
  [?hist-lines <- (acc/all) :from [HistoricalLine]]
  =>
  (when (seq ?cur-lines)
    (let [ref-date (:service-date (first ?cur-lines))
          this-yr-cur  (filter #(d/same-benefit-year? (:service-date %) ref-date) ?cur-lines)
          this-yr-hist (filter #(d/same-benefit-year? (:service-date %) ref-date) ?hist-lines)
          current-charges  (reduce + 0.0 (map :charge this-yr-cur))
          historical-spend (reduce + 0.0 (map :allowed-amount this-yr-hist))
          combined         (+ current-charges historical-spend)
          max-amount       (:max-amount ?params)]
      (when (> combined max-amount)
        (insert!
         (f/->Finding (:rule-id ?rule)
                    :annual-maximum
                    (:severity ?rule)
                    (:reason-code ?rule)
                    (str "Combined benefit-year spend $"
                         (format "%.2f" combined)
                         " exceeds annual max $"
                         (format "%.2f" max-amount))
                    (mapv :line-number this-yr-cur)
                    (:citation ?rule)
                    {:historical-spend historical-spend
                     :current-charges  current-charges
                     :combined         combined
                     :max-amount       max-amount
                     :remaining-benefit (max 0.0 (- max-amount historical-spend))}))))))

(defrule fee-schedule-application
  "Apply the in-network fee schedule: any line whose charge exceeds the
   scheduled allowed amount emits an :adjust finding for the difference."
  [?rule <- CatalogRule (= category :fee-schedule) (= ?params params)]
  [?line <- ServiceLine (contains? (:schedule ?params) procedure-code)]
  =>
  (let [allowed (get-in ?params [:schedule (:procedure-code ?line)])
        charge  (:charge ?line)
        adjust  (- charge allowed)]
    (when (> adjust 0)
      (insert!
       (f/->Finding (:rule-id ?rule)
                  :fee-schedule
                  :adjust
                  (:reason-code ?rule)
                  (str "Line " (:line-number ?line) " charge $"
                       (format "%.2f" charge)
                       " exceeds scheduled allowed $"
                       (format "%.2f" allowed)
                       " (adjustment: $" (format "%.2f" adjust) ")")
                  [(:line-number ?line)]
                  (:citation ?rule)
                  {:charged charge :allowed allowed :adjustment adjust})))))

;; ──────────────────────────────────────────────────────────────────────────
;; Queries — read facts back out of the session
;; ──────────────────────────────────────────────────────────────────────────

(defquery findings-query
  "All findings emitted during this session."
  []
  [?finding <- Finding])

(defquery service-lines-query
  []
  [?line <- ServiceLine])

(defquery catalog-rules-query
  []
  [?rule <- CatalogRule])
