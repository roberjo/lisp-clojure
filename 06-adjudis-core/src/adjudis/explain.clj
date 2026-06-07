(ns adjudis.explain
  "Combine Findings into a structured decision: per-line verdicts, dollar
   amounts, an overall verdict, and a full citation chain.

   Every decision is fully reconstructable from (claim + catalog + findings).
   The explanation graph is the audit trail."
  (:require [adjudis.schema :as schema]))

(defn- max-severity-of [findings]
  (when (seq findings)
    (->> findings
         (map :severity)
         (sort-by schema/severity-rank >)
         first)))

(def severity->verdict
  {:deny    :denied
   :pending :pending
   :adjust  :paid       ;; adjustments alone don't change the verdict
   :warn    :paid
   :inform  :paid})

(defn- verdict-of [findings line-charge]
  (if-let [sev (max-severity-of findings)]
    (severity->verdict sev)
    :paid))

(defn- adjustment-for [findings]
  (->> findings
       (filter #(= :fee-schedule (:rule-category %)))
       (map #(get-in % [:extra-context :adjustment] 0))
       (reduce + 0)))

(defn- allowed-for [findings line-charge]
  (max 0.0 (- line-charge (adjustment-for findings))))

(defn- patient-coinsurance
  "MVP coinsurance model: 80/20 PPO split on dental basic services."
  [allowed]
  (* allowed 0.20))

(defn- line-decision [line line-findings]
  (let [verdict   (verdict-of line-findings (:charge line))
        allowed   (if (= :denied verdict) 0.0 (allowed-for line-findings (:charge line)))
        pat-resp  (if (= :paid verdict) (patient-coinsurance allowed) 0.0)]
    {:line-number             (:line-number line)
     :procedure-code          (:procedure-code line)
     :service-date            (:service-date line)
     :charged                 (:charge line)
     :allowed                 allowed
     :patient-responsibility  pat-resp
     :provider-payment        (- allowed pat-resp)
     :verdict                 verdict
     :reason-codes            (->> line-findings
                                   (map :reason-code)
                                   distinct
                                   vec)}))

(defn- partition-findings-by-line [lines findings]
  (let [line-numbers (set (map :line-number lines))
        ;; A finding with affected-lines [] is claim-level; we'll attach it to every line.
        per-line     (into {} (for [n line-numbers] [n []]))
        per-line     (reduce
                      (fn [acc f]
                        (let [aff (:affected-lines f)
                              targets (if (seq aff) aff line-numbers)]
                          (reduce (fn [m n] (update m n (fnil conj []) f))
                                  acc
                                  targets)))
                      per-line
                      findings)]
    per-line))

(defn build-decision
  "Turn a list of Findings into the schema-conformant decision map."
  [claim findings]
  (let [lines             (:service-lines claim)
        findings-per-line (partition-findings-by-line lines findings)
        line-decisions    (mapv (fn [line]
                                  (line-decision line (get findings-per-line
                                                           (:line-number line)
                                                           [])))
                                lines)
        line-verdicts     (set (map :verdict line-decisions))
        overall-verdict   (cond
                            (line-verdicts :pending) :pending
                            (= #{:denied} line-verdicts) :denied
                            (line-verdicts :denied) :partial      ;; some lines paid, some denied
                            :else :paid)
        total-allowed     (reduce + 0.0 (map :allowed line-decisions))
        total-patient     (reduce + 0.0 (map :patient-responsibility line-decisions))
        total-provider    (reduce + 0.0 (map :provider-payment line-decisions))]
    {:claim-id                (:claim-id claim)
     :verdict                 overall-verdict
     :total-charged           (reduce + 0.0 (map :charge lines))
     :total-allowed           total-allowed
     :patient-responsibility  total-patient
     :provider-payment        total-provider
     :line-decisions          line-decisions
     :findings                (vec findings)
     :rule-versions           {:catalog-version schema/catalog-version
                               :engine-version  schema/engine-version}}))
