(ns adjudis.versions-test
  (:require [clojure.test :refer [deftest is testing]]
            [adjudis.versions :as v]))

(def base-rule
  {:rule-id "X" :category :frequency-limit :description "x" :severity :deny
   :reason-code "X" :params {} :citation {}})

(deftest rule-without-effective-metadata-always-active
  (is (v/rule-active-on? base-rule "2024-06-15"))
  (is (v/rule-active-on? base-rule nil)))

(deftest rule-active-on-respects-bounds
  (let [r (assoc base-rule :effective-from "2024-01-01" :effective-to "2024-12-31")]
    (is (v/rule-active-on? r "2024-06-15"))
    (is (v/rule-active-on? r "2024-01-01"))
    (is (v/rule-active-on? r "2024-12-31"))
    (is (not (v/rule-active-on? r "2023-12-31")))
    (is (not (v/rule-active-on? r "2025-01-01")))))

(deftest rule-open-bounds-mean-open-ended
  (let [r-from-only (assoc base-rule :effective-from "2024-01-01")
        r-to-only   (assoc base-rule :effective-to   "2024-12-31")]
    (is (v/rule-active-on? r-from-only "2099-06-15"))
    (is (not (v/rule-active-on? r-from-only "2023-12-31")))
    (is (v/rule-active-on? r-to-only "1999-06-15"))
    (is (not (v/rule-active-on? r-to-only "2025-01-01")))))

(deftest active-rules-filters-catalog
  (let [cat [(assoc base-rule :rule-id "A" :effective-to "2024-06-30")
             (assoc base-rule :rule-id "B" :effective-from "2024-07-01")
             (assoc base-rule :rule-id "C")]]    ; always active
    (is (= #{"A" "C"} (set (map :rule-id (v/active-rules cat "2024-05-15")))))
    (is (= #{"B" "C"} (set (map :rule-id (v/active-rules cat "2024-09-01")))))))

(deftest diff-catalogs-detects-changes
  (let [a [(assoc base-rule :rule-id "R1")
           (assoc base-rule :rule-id "R2" :severity :deny)]
        b [(assoc base-rule :rule-id "R2" :severity :pending)   ; changed
           (assoc base-rule :rule-id "R3")]]                    ; added
    (let [d (v/diff-catalogs a b)]
      (is (= ["R3"] (mapv :rule-id (:added d))))
      (is (= ["R1"] (mapv :rule-id (:removed d))))
      (is (= ["R2"] (mapv :rule-id (:changed d)))))))
