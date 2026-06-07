(ns adjudis.shadow-test
  (:require [clojure.test :refer [deftest is testing]]
            [adjudis.shadow  :as shadow]
            [adjudis.history :as hist]))

(def adult-member
  {:subscriber-id  "M00112233"
   :date-of-birth  "1985-03-12"
   :coverage-start "2024-01-01"
   :coverage-end   "2024-12-31"
   :plan-type      :ppo})

(defn- claim [id code charge dos]
  {:claim-id         id
   :control-number   id
   :transaction-type :dental
   :billing-provider "ACME DENTAL"
   :subscriber       {:member-id "M00112233"}
   :network          :in-network
   :total-charge     charge
   :service-lines    [{:line-number 1 :procedure-code code :charge charge
                       :service-date dos :units 1}]})

(def base-catalog
  [{:rule-id "FREQ-LOOSE"
    :category :frequency-limit
    :description "loose freq limit (5/year)"
    :severity :deny :reason-code "FREQ"
    :params {:procedure-codes #{"D1110"} :max-per-year 5}
    :citation {:source "test"}}])

(def tighter-catalog
  [{:rule-id "FREQ-TIGHT"
    :category :frequency-limit
    :description "tight freq limit (1/year)"
    :severity :deny :reason-code "FREQ"
    :params {:procedure-codes #{"D1110"} :max-per-year 1}
    :citation {:source "test"}}])

(defn- history-with-prior-prophy []
  (hist/make-atom-store
   {"M00112233"
    [#adjudis.facts.HistoricalLine{:subscriber-id "M00112233"
                                   :procedure-code "D1110"
                                   :service-date "2024-03-01"
                                   :allowed-amount 100.0}]}))

(deftest shadow-detects-tightening
  ;; Member has one prior D1110; current claim adds a second.
  ;; Loose catalog (max=5): under limit, paid. Neither rule fires.
  ;; Tight catalog (max=1): count of 2 exceeds, FREQ-TIGHT fires → denied.
  ;; rules-added = {FREQ-TIGHT}; rules-removed = {} (loose never fired to begin with).
  (let [result (shadow/adjudicate-shadow
                (claim "C-SHADOW" "D1110" 100.0 "2024-09-15")
                adult-member
                {:history (history-with-prior-prophy)
                 :catalog base-catalog
                 :proposed-catalog tighter-catalog})]
    (is (= :paid    (get-in result [:current :verdict])))
    (is (= :denied  (get-in result [:proposed :verdict])))
    (is (= :tightened (get-in result [:delta :verdict-change])))
    (is (contains? (get-in result [:delta :rules-added]) "FREQ-TIGHT"))
    (is (empty?    (get-in result [:delta :rules-removed]))
        "loose rule never fired, so nothing to 'remove' under proposed")))

(deftest shadow-batch-summary-aggregates
  ;; Same history setup for all three; expect all to tighten.
  (let [history (history-with-prior-prophy)
        pairs   (repeat 3 [(claim "C-BATCH" "D1110" 100.0 "2024-09-15") adult-member])
        summary (shadow/batch-shadow-summary
                 pairs
                 {:history history :catalog base-catalog :proposed-catalog tighter-catalog})]
    (is (= 3 (:n summary)))
    (is (= 3 (get-in summary [:changes :tightened]))
        (str "expected all 3 to tighten, got " (:changes summary)))
    ;; Each tightened claim eliminates a $100 provider payment (approx, minus coinsurance).
    (is (neg? (:total-delta summary))
        "tightening should reduce total provider payment")))
