(ns adjudis.tenants-test
  (:require [clojure.test :refer [deftest is testing]]
            [adjudis.tenants :as t]))

(def base
  [{:rule-id "R1" :category :frequency-limit :description "shipped #1"
    :severity :deny :reason-code "R1" :params {} :citation {}}
   {:rule-id "R2" :category :age-appropriate :description "shipped #2"
    :severity :deny :reason-code "R2" :params {} :citation {}}
   {:rule-id "R3" :category :fee-schedule :description "shipped #3"
    :severity :adjust :reason-code "R3" :params {} :citation {}}])

(deftest apply-overlay-empty-leaves-base-unchanged
  (is (= base (t/apply-overlay base {})))
  (is (= base (t/apply-overlay base {:add [] :override [] :remove #{}}))))

(deftest apply-overlay-add-appends
  (let [new-rule {:rule-id "R4" :category :pre-auth-required
                  :description "tenant-only" :severity :pending
                  :reason-code "R4" :params {} :citation {}}
        result   (t/apply-overlay base {:add [new-rule]})]
    (is (= 4 (count result)))
    (is (= "R4" (:rule-id (last result))))))

(deftest apply-overlay-remove-suppresses-by-id
  (let [result (t/apply-overlay base {:remove #{"R2"}})]
    (is (= 2 (count result)))
    (is (= #{"R1" "R3"} (set (map :rule-id result))))))

(deftest apply-overlay-override-replaces-by-id
  (let [tweaked  {:rule-id "R2" :category :age-appropriate
                  :description "tenant tightened" :severity :pending
                  :reason-code "R2-TWEAK" :params {} :citation {}}
        result   (t/apply-overlay base {:override [tweaked]})]
    (is (= 3 (count result)))
    (let [r2 (first (filter #(= "R2" (:rule-id %)) result))]
      (is (= "tenant tightened" (:description r2)))
      (is (= :pending (:severity r2)))
      (is (= "R2-TWEAK" (:reason-code r2))))))

(deftest apply-overlay-override-takes-precedence-over-remove
  ;; If a rule-id appears in both :remove and :override, the override wins
  ;; (composition order: removes apply first, then override re-introduces).
  (let [overridden {:rule-id "R2" :category :age-appropriate
                    :description "kept via override"
                    :severity :deny :reason-code "R2" :params {} :citation {}}
        result     (t/apply-overlay base {:remove   #{"R2"}
                                          :override [overridden]})]
    (is (some #(= "R2" (:rule-id %)) result))))

(deftest apply-overlay-add-and-remove-and-override-compose
  (let [overridden {:rule-id "R1" :category :frequency-limit
                    :description "tightened" :severity :deny
                    :reason-code "R1" :params {} :citation {}}
        new-rule   {:rule-id "R-NEW" :category :pre-auth-required
                    :description "tenant only" :severity :pending
                    :reason-code "NEW" :params {} :citation {}}
        result     (t/apply-overlay base {:override [overridden]
                                          :add      [new-rule]
                                          :remove   #{"R3"}})]
    (is (= 3 (count result)))
    (is (= #{"R1" "R2" "R-NEW"} (set (map :rule-id result))))
    (is (= "tightened" (:description (first (filter #(= "R1" (:rule-id %)) result)))))))

(deftest lookup-by-api-key
  (let [registry {"a" {:api-key "key-a" :name "A"}
                  "b" {:api-key "key-b" :name "B"}}]
    (is (= "A" (:name  (t/lookup-by-api-key registry "key-a"))))
    (is (= "a" (:tenant-id (t/lookup-by-api-key registry "key-a"))))
    (is (nil? (t/lookup-by-api-key registry "key-bogus")))
    (is (nil? (t/lookup-by-api-key registry nil)))))

(deftest load-shipped-tenants-fixture
  (let [tenants (t/load-tenants "resources/fixtures/tenants.edn")]
    (is (= 2 (count tenants)))
    (is (contains? tenants "acme-dental"))
    (is (contains? tenants "beta-carrier"))))
