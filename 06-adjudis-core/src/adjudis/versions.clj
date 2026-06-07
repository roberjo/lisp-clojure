(ns adjudis.versions
  "Catalog versioning: filter a rule list to those effective on a given date.

   A rule's effective window is [effective-from, effective-to] inclusive; nil
   bounds mean open-ended. A rule without either field is always effective.

   This is the foundation for shadow-mode and replay: the engine never asks
   'is this rule version current?', it asks 'was this rule active on the
   service date?'. Same code path adjudicates today's claims and replays
   five-year-old ones."
  (:require [adjudis.dates :as d]))

(defn rule-active-on?
  "True if the rule has no version metadata, or its window contains dos."
  [rule dos]
  (let [from (:effective-from rule)
        to   (:effective-to   rule)]
    (cond
      (and (nil? from) (nil? to)) true
      (nil? dos)                  true   ;; can't filter without a reference date
      :else (d/within? dos from to))))

(defn active-rules
  "Filter catalog to rules whose effective window covers reference-date."
  [catalog reference-date]
  (filterv #(rule-active-on? % reference-date) catalog))

(defn rule-by-id [catalog rule-id]
  (some #(when (= rule-id (:rule-id %)) %) catalog))

(defn diff-catalogs
  "Compare two catalogs by rule-id. Returns
     {:added   [<rule>...]      ; in b, not in a
      :removed [<rule>...]      ; in a, not in b
      :changed [{:rule-id ..., :before ..., :after ...} ...]}"
  [a b]
  (let [by-id (fn [cat] (into {} (map (juxt :rule-id identity) cat)))
        a-map (by-id a)
        b-map (by-id b)
        ids-a (set (keys a-map))
        ids-b (set (keys b-map))]
    {:added   (mapv b-map (sort (clojure.set/difference ids-b ids-a)))
     :removed (mapv a-map (sort (clojure.set/difference ids-a ids-b)))
     :changed (->> (clojure.set/intersection ids-a ids-b)
                   sort
                   (keep (fn [id]
                           (let [before (a-map id)
                                 after  (b-map id)]
                             (when (not= before after)
                               {:rule-id id :before before :after after}))))
                   vec)}))
