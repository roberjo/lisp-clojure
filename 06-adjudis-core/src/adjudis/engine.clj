(ns adjudis.engine
  "The public adjudication API. Hides Clara session construction and
   catalog/history loading from callers."
  (:require [clara.rules :as cr]
            [adjudis.catalog :as catalog]
            [adjudis.facts   :as f]
            [adjudis.rules   :as rules]
            [adjudis.history :as hist]
            [adjudis.explain :as explain]))

(defn- claim-input->facts
  "Convert the normalized claim map (project-04 shape) + member map to Clara
   fact records."
  [claim member]
  (let [claim-fact
        (f/->Claim (:claim-id claim)
                   (:control-number claim)
                   (get-in claim [:subscriber :member-id])
                   (:billing-provider claim)
                   (or (:network claim) :in-network)
                   (:total-charge claim)
                   (:pre-auth-reference claim)
                   (:transaction-type claim))
        line-facts
        (for [line (:service-lines claim)]
          (f/->ServiceLine (:claim-id claim)
                           (:line-number line)
                           (:procedure-code line)
                           (:service-date line)
                           (:charge line)
                           (or (:units line) 1)))
        member-fact
        (f/->Member (:subscriber-id member)
                    (:date-of-birth member)
                    (:coverage-start member)
                    (:coverage-end member)
                    (or (:plan-type member) :ppo))]
    (into [claim-fact member-fact] line-facts)))

(defn- catalog->facts [catalog-rules]
  (mapv #(f/map->CatalogRule %) catalog-rules))

(defn adjudicate
  "Adjudicate a single claim.

   claim   — normalized claim map (same shape project 04 emits, plus
             optional :pre-auth-reference and :network).
   member  — {:subscriber-id :date-of-birth :coverage-start :coverage-end}.
   opts    — {:history (HistoryStore)
              :catalog (vector of catalog rules; defaults to loaded library)}

   Returns the decision map (see adjudis.schema)."
  [claim member {:keys [history catalog]
                 :or   {history (hist/make-atom-store)
                        catalog (catalog/load-catalog)}}]
  (let [historical    (hist/lookup-lines history (:subscriber-id member))
        catalog-facts (catalog->facts catalog)
        claim-facts   (claim-input->facts claim member)
        all-facts     (concat claim-facts catalog-facts historical)
        session       (-> (cr/mk-session 'adjudis.rules)
                          (cr/insert-all all-facts)
                          (cr/fire-rules))
        findings      (mapv :?finding (cr/query session rules/findings-query))]
    (explain/build-decision claim findings)))

(defn adjudicate-and-record!
  "Adjudicate, then persist the decision into the history store. Convenience
   for callers that own the store."
  [claim member opts]
  (let [decision (adjudicate claim member opts)]
    (hist/record-decision! (:history opts)
                           (get-in claim [:subscriber :member-id])
                           decision)
    decision))
