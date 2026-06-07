(ns adjudis.engine-versioned-test
  (:require [clojure.test :refer [deftest is testing]]
            [adjudis.engine :as engine]))

(def member
  {:subscriber-id  "M00112233"
   :date-of-birth  "1985-03-12"
   :coverage-start "2024-01-01"
   :coverage-end   "2099-12-31"
   :plan-type      :ppo})

(defn- claim-on [dos]
  {:claim-id         "C-VER"
   :control-number   "1"
   :transaction-type :dental
   :billing-provider "ACME DENTAL"
   :subscriber       {:member-id "M00112233"}
   :network          :in-network
   :total-charge     1000.0
   :service-lines    [{:line-number 1 :procedure-code "D2740" :charge 1000.0
                       :service-date dos :units 1}]})

;; A pre-auth rule effective only in 2024.
(def catalog-2024
  [{:rule-id "PREAUTH-2024-ONLY"
    :category :pre-auth-required
    :description "Pre-auth required for D2740 in 2024 only."
    :severity :pending :reason-code "PA"
    :params {:procedure-codes #{"D2740"} :threshold-amount 500.0}
    :citation {:source "test"}
    :effective-from "2024-01-01"
    :effective-to   "2024-12-31"}])

(deftest rule-active-when-service-date-inside-window
  (let [d (engine/adjudicate (claim-on "2024-09-15") member {:catalog catalog-2024})]
    (is (= :pending (:verdict d)))
    (is (some #(= "PREAUTH-2024-ONLY" (:rule-id %)) (:findings d)))))

(deftest rule-skipped-when-service-date-after-window
  (let [d (engine/adjudicate (claim-on "2025-01-15") member {:catalog catalog-2024})]
    (is (= :paid (:verdict d)))
    (is (empty? (:findings d)))))

(deftest rule-skipped-when-service-date-before-window
  (let [d (engine/adjudicate (claim-on "2023-12-15") member {:catalog catalog-2024})]
    (is (= :paid (:verdict d)))
    (is (empty? (:findings d)))))

(deftest as-of-override-takes-precedence
  ;; Claim on 2025-01-15, but we adjudicate as-of 2024-09-15 (replay scenario).
  (let [d (engine/adjudicate (claim-on "2025-01-15") member
                             {:catalog catalog-2024 :as-of "2024-09-15"})]
    (is (= :pending (:verdict d)))))
