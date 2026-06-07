(ns adjudis.history
  "Claim-history store. MVP is an in-process atom with the same API surface
   XTDB will later implement, so callers don't change between phases.

   The store records per-subscriber adjudicated lines: just enough for
   frequency-limit and annual-max rules to reason about prior visits.

   Phase 2 will swap in XTDB for bitemporal queries ('what was this member's
   adjudicated spend as of June 1?') and full audit trails."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [adjudis.facts :as f]))

(defprotocol HistoryStore
  (lookup-lines [this subscriber-id]
    "Return a sequence of HistoricalLine records for the subscriber.")
  (record-decision! [this subscriber-id decision]
    "Persist a decision's allowed lines so future adjudications see them."))

;; ── In-memory implementation ──────────────────────────────────────────────

(defrecord AtomStore [state]
  HistoryStore
  (lookup-lines [_ subscriber-id]
    (get @state subscriber-id []))
  (record-decision! [_ subscriber-id decision]
    (swap! state update subscriber-id
           (fnil into [])
           (for [ld (:line-decisions decision)
                 :when (= :paid (:verdict ld))]
             (f/->HistoricalLine subscriber-id
                                 (:procedure-code ld)
                                 (:service-date ld)
                                 (:allowed ld))))))

(defn make-atom-store
  ([] (make-atom-store {}))
  ([initial-state] (->AtomStore (atom initial-state))))

;; ── EDN-fixture loader (for tests / CLI demo) ─────────────────────────────

(defn load-fixture
  "Read an EDN file shaped as {subscriber-id [{:procedure-code ... :service-date ... :allowed-amount ...} ...]}
   and return an AtomStore prepopulated with HistoricalLine records."
  [path]
  (let [raw (with-open [r (java.io.PushbackReader. (io/reader path))]
              (edn/read r))
        seeded (into {}
                     (for [[sid lines] raw]
                       [sid (mapv (fn [m]
                                    (f/map->HistoricalLine
                                     (assoc m :subscriber-id sid)))
                                  lines)]))]
    (make-atom-store seeded)))
