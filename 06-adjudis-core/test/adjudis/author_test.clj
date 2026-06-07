(ns adjudis.author-test
  (:require [clojure.test :refer [deftest is testing]]
            [adjudis.author :as author]))

(def good
  [{:rule-id "OK-1" :category :frequency-limit
    :description "x" :severity :deny :reason-code "R" :params {}
    :citation {:source "x"}}])

(def bad-missing-keys
  [{:rule-id "BAD-1" :category :frequency-limit}])

(def bad-unknown-category
  [{:rule-id "BAD-2" :category :totally-made-up
    :description "x" :severity :deny :reason-code "R" :params {}
    :citation {:source "x"}}])

(def bad-unknown-severity
  [{:rule-id "BAD-3" :category :frequency-limit
    :description "x" :severity :super-deny :reason-code "R" :params {}
    :citation {:source "x"}}])

(def duplicate-ids
  [{:rule-id "DUP" :category :frequency-limit
    :description "x" :severity :deny :reason-code "R" :params {} :citation {}}
   {:rule-id "DUP" :category :frequency-limit
    :description "y" :severity :deny :reason-code "R" :params {} :citation {}}])

(deftest validate-passes-clean
  (let [r (author/validate-catalog good)]
    (is (:ok? r))
    (is (empty? (:rules-with-issues r)))
    (is (empty? (:duplicate-ids r)))))

(deftest validate-flags-missing-required-keys
  (let [r (author/validate-catalog bad-missing-keys)]
    (is (not (:ok? r)))
    (let [issues (:rules-with-issues r)]
      (is (= 1 (count issues)))
      (is (some #(re-find #"missing" %) (-> issues first :issues))))))

(deftest validate-flags-unknown-category
  (let [r (author/validate-catalog bad-unknown-category)]
    (is (not (:ok? r)))
    (is (some #(re-find #"unknown category" %)
              (-> r :rules-with-issues first :issues)))))

(deftest validate-flags-unknown-severity
  (let [r (author/validate-catalog bad-unknown-severity)]
    (is (not (:ok? r)))
    (is (some #(re-find #"unknown severity" %)
              (-> r :rules-with-issues first :issues)))))

(deftest validate-flags-duplicate-ids
  (let [r (author/validate-catalog duplicate-ids)]
    (is (not (:ok? r)))
    (is (= ["DUP"] (:duplicate-ids r)))))

(deftest validate-shipped-catalog
  ;; Sanity: the actual library on disk passes validation.
  (let [shipped (adjudis.catalog/load-catalog)
        result  (author/validate-catalog shipped)]
    (is (:ok? result)
        (str "shipped catalog has issues: " (pr-str result)))))
