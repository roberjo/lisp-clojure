(ns adjudis.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [adjudis.engine  :as engine]
            [adjudis.facts   :as f]
            [adjudis.history :as hist]))

;; ──────────────────────────────────────────────────────────────────────────
;; Test scaffolding
;; ──────────────────────────────────────────────────────────────────────────

(def adult-member
  {:subscriber-id   "M00112233"
   :date-of-birth   "1985-03-12"
   :coverage-start  "2024-01-01"
   :coverage-end    "2024-12-31"
   :plan-type       :ppo})

(def child-member
  {:subscriber-id   "M00112299"
   :date-of-birth   "2018-08-04"
   :coverage-start  "2024-01-01"
   :coverage-end    "2024-12-31"
   :plan-type       :ppo})

(defn- line [n code charge dos & {:keys [units] :or {units 1}}]
  {:line-number n :procedure-code code :charge charge :service-date dos :units units})

(defn- claim [id member-id lines & {:keys [pre-auth network] :or {network :in-network}}]
  {:claim-id           id
   :control-number     id
   :transaction-type   :dental
   :billing-provider   "ACME DENTAL"
   :subscriber         {:member-id member-id}
   :network            network
   :pre-auth-reference pre-auth
   :total-charge       (reduce + 0.0 (map :charge lines))
   :service-lines      lines})

(defn- has-finding-with-rule? [decision rule-id]
  (boolean (some #(= rule-id (:rule-id %)) (:findings decision))))

(defn- has-finding-with-category? [decision category]
  (boolean (some #(= category (:rule-category %)) (:findings decision))))

;; ──────────────────────────────────────────────────────────────────────────
;; Tests, one per rule class + integration
;; ──────────────────────────────────────────────────────────────────────────

(deftest clean-claim-pays
  (let [c (claim "C-CLEAN" "M00112233"
                 [(line 1 "D0120" 50.00 "2024-09-15")])
        d (engine/adjudicate c adult-member {})]
    (is (= :paid (:verdict d))
        (str "expected :paid, got " (:verdict d) ", findings: " (pr-str (:findings d))))
    (is (= 0 (count (filter #(= :deny (:severity %)) (:findings d)))))))

(deftest frequency-rule-fires-on-third-prophy
  (let [history (hist/load-fixture "resources/fixtures/history-doe-jane.edn")
        c (claim "C-FREQ" "M00112233"
                 [(line 1 "D1110" 150.00 "2024-12-01")])
        d (engine/adjudicate c adult-member {:history history})]
    (is (has-finding-with-rule? d "DENTAL-PROPHY-ADULT-FREQ"))
    (is (= :denied (:verdict d)))))

(deftest age-rule-denies-child-prophy-on-adult
  (let [c (claim "C-AGE-ADULT" "M00112233"
                 [(line 1 "D1120" 100.00 "2024-09-15")])
        d (engine/adjudicate c adult-member {})]
    (is (has-finding-with-rule? d "DENTAL-CHILD-PROPHY-AGE"))
    (is (= :denied (:verdict d)))))

(deftest age-rule-denies-adult-prophy-on-child
  (let [c (claim "C-AGE-CHILD" "M00112299"
                 [(line 1 "D1110" 100.00 "2024-09-15")])
        d (engine/adjudicate c child-member {})]
    (is (has-finding-with-rule? d "DENTAL-ADULT-PROPHY-AGE"))
    (is (= :denied (:verdict d)))))

(deftest sealant-allowed-for-child-denied-for-adult
  (testing "child"
    (let [c (claim "C-SEAL-OK" "M00112299"
                   [(line 1 "D1351" 65.00 "2024-09-15")])
          d (engine/adjudicate c child-member {})]
      (is (= :paid (:verdict d)))
      (is (not (has-finding-with-rule? d "DENTAL-SEALANT-AGE")))))
  (testing "adult"
    (let [c (claim "C-SEAL-NO" "M00112233"
                   [(line 1 "D1351" 65.00 "2024-09-15")])
          d (engine/adjudicate c adult-member {})]
      (is (= :denied (:verdict d)))
      (is (has-finding-with-rule? d "DENTAL-SEALANT-AGE")))))

(deftest pre-auth-pends-large-crown
  (let [c (claim "C-PREAUTH" "M00112233"
                 [(line 1 "D2740" 1100.00 "2024-09-15")])
        d (engine/adjudicate c adult-member {})]
    (is (has-finding-with-rule? d "DENTAL-PREAUTH-CROWN"))
    (is (= :pending (:verdict d)))))

(deftest pre-auth-bypassed-when-reference-present
  (let [c (claim "C-PREAUTH-OK" "M00112233"
                 [(line 1 "D2740" 1100.00 "2024-09-15")]
                 :pre-auth "AUTH-12345")
        d (engine/adjudicate c adult-member {})]
    (is (not (has-finding-with-rule? d "DENTAL-PREAUTH-CROWN")))))

(deftest eligibility-denies-service-outside-coverage
  (let [out-of-coverage (assoc adult-member :coverage-end "2024-06-30")
        c (claim "C-ELIG" "M00112233"
                 [(line 1 "D0120" 50.00 "2024-12-15")])
        d (engine/adjudicate c out-of-coverage {})]
    (is (has-finding-with-rule? d "MEMBER-ACTIVE-ON-DOS"))
    (is (= :denied (:verdict d)))))

(deftest fee-schedule-applies-adjustment
  (let [c (claim "C-FEE" "M00112233"
                 [(line 1 "D1110" 250.00 "2024-09-15")])   ; charged 250, allowed 100
        d (engine/adjudicate c adult-member {})
        line1 (first (:line-decisions d))]
    (is (has-finding-with-category? d :fee-schedule))
    (is (= :paid (:verdict line1)))
    (is (= 100.00 (:allowed line1)))
    (is (= 150.00 (- (:charged line1) (:allowed line1))))))

(deftest annual-max-pends-claim-over-cap
  ;; Seed history with $1400 of allowed amounts; current claim is $200 → over the $1500 cap.
  (let [seeded (hist/make-atom-store
                {"M00112233" [(f/->HistoricalLine "M00112233" "D2740" "2024-04-01" 1400.00)]})
        c (claim "C-MAX" "M00112233"
                 [(line 1 "D2150" 200.00 "2024-09-15")])
        d (engine/adjudicate c adult-member {:history seeded})]
    (is (has-finding-with-rule? d "DENTAL-ANNUAL-MAX-DEFAULT"))
    (is (= :pending (:verdict d)))))

(deftest explanation-carries-full-citation-chain
  (let [c (claim "C-EXPLAIN" "M00112233"
                 [(line 1 "D2740" 1100.00 "2024-09-15")])
        d (engine/adjudicate c adult-member {})]
    (doseq [f (:findings d)]
      (is (some? (:citation f)) (str "missing citation on " (:rule-id f)))
      (is (some? (:reason-code f)))
      (is (some? (:message f))))))

(deftest decision-shape-conforms-to-schema
  (let [c (claim "C-SHAPE" "M00112233"
                 [(line 1 "D0120" 50.00 "2024-09-15")])
        d (engine/adjudicate c adult-member {})]
    (is (contains? d :verdict))
    (is (contains? d :total-charged))
    (is (contains? d :total-allowed))
    (is (contains? d :patient-responsibility))
    (is (contains? d :provider-payment))
    (is (contains? d :line-decisions))
    (is (contains? d :findings))
    (is (contains? d :rule-versions))))

(deftest history-records-paid-decision
  (let [store (hist/make-atom-store)
        c (claim "C-RECORD" "M00112233"
                 [(line 1 "D0120" 50.00 "2024-09-15")])
        _ (engine/adjudicate-and-record! c adult-member {:history store})
        recorded (hist/lookup-lines store "M00112233")]
    (is (= 1 (count recorded)))
    (is (= "D0120" (:procedure-code (first recorded))))))
