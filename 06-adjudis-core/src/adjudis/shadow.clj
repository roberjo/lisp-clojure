(ns adjudis.shadow
  "Shadow-mode adjudication: run a claim against the CURRENT catalog and a
   PROPOSED catalog, return both decisions plus a delta. The proposed
   decision does not affect the live payout.

   This is the cornerstone of safe rule-change rollout. A PM proposing
   'tighten frequency rules from 2/year to 1/year' can ask:

       — Re-adjudicate the last 30 days of claims under the proposed catalog.
       — What's the verdict-change distribution?
       — Which claims would flip from :paid to :denied?
       — What's the dollar impact on provider payments?

   Without this, rule changes are released by faith. With it, they're released
   by data."
  (:require [adjudis.engine :as engine]))

(defn- verdict-flip-kind
  "Classify the relationship between two verdicts."
  [current-v proposed-v]
  (cond
    (= current-v proposed-v)                           :unchanged
    (and (= :paid current-v) (= :denied   proposed-v)) :tightened     ;; was paid, now denied
    (and (= :paid current-v) (= :pending  proposed-v)) :tightened
    (and (= :paid current-v) (= :partial  proposed-v)) :tightened
    (and (= :denied current-v) (= :paid   proposed-v)) :loosened
    (and (= :pending current-v) (= :paid  proposed-v)) :loosened
    :else                                              :reclassified))

(defn- dollars-delta [current proposed]
  (- (:provider-payment proposed 0.0)
     (:provider-payment current 0.0)))

(defn- rule-set [decision]
  (->> (:findings decision)
       (map :rule-id)
       set))

(defn adjudicate-shadow
  "Adjudicate against current AND proposed catalogs. Returns:

     {:current  <decision under current catalog>
      :proposed <decision under proposed catalog>
      :delta    {:verdict-change  :unchanged | :tightened | :loosened | :reclassified
                 :dollars         <provider-payment delta, positive = proposed pays more>
                 :rules-added     #{rule-ids newly firing}
                 :rules-removed   #{rule-ids no longer firing}}}

   :proposed-catalog is the full proposed rule set (NOT a diff overlay).
   To test 'add one rule', concat it onto the current catalog and pass that."
  [claim member {:keys [history catalog proposed-catalog as-of] :as opts}]
  (let [current   (engine/adjudicate claim member opts)
        proposed  (engine/adjudicate claim member
                                     (assoc opts :catalog proposed-catalog))
        cur-rules (rule-set current)
        new-rules (rule-set proposed)]
    {:current  current
     :proposed proposed
     :delta    {:verdict-change (verdict-flip-kind (:verdict current) (:verdict proposed))
                :dollars        (dollars-delta current proposed)
                :rules-added    (into (sorted-set) (clojure.set/difference new-rules cur-rules))
                :rules-removed  (into (sorted-set) (clojure.set/difference cur-rules new-rules))}}))

(defn batch-shadow-summary
  "Run shadow adjudication across many (claim, member) pairs and summarize
   the distribution of verdict changes and dollar impact.

   pairs — sequence of [claim member] tuples.
   opts  — same as adjudicate-shadow."
  [pairs opts]
  (let [results (mapv (fn [[c m]] (adjudicate-shadow c m opts)) pairs)
        changes (frequencies (map #(get-in % [:delta :verdict-change]) results))
        dollars (reduce + 0.0 (map #(get-in % [:delta :dollars]) results))]
    {:n           (count results)
     :changes     changes
     :total-delta dollars
     :details     results}))
