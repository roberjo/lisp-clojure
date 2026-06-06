(ns edi.transform.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn  :as edn]
            [edi.transform.core   :as core]
            [edi.transform.schema :as schema]))

(defn- load-fixture []
  (-> (slurp "resources/fixtures/minimal-837d.edn")
      edn/read-string))

(deftest plist-map-conversion
  (is (= {:a 1 :b 2} (core/plist->map [:a 1 :b 2]))))

(deftest segment-conversion
  (let [seg (core/segment-plist->map [:id "CLM" :elements ["PCN001" "250.00"]])]
    (is (= "CLM" (:id seg)))
    (is (= ["PCN001" "250.00"] (:elements seg)))))

(deftest full-fixture-transforms-to-valid-claim
  (let [tx-pl (load-fixture)
        claim (core/transaction->claim tx-pl)]
    (testing "shape conforms to NormalizedClaim schema"
      (is (nil? (schema/validate-claim claim))
          (str "validation failed: " (pr-str (schema/validate-claim claim)))))
    (testing "key fields are extracted correctly"
      (is (= :dental    (:transaction-type claim)))
      (is (= "0001"     (:control-number   claim)))
      (is (= "PCN001"   (:claim-id         claim)))
      (is (= 250.00     (:total-charge     claim)))
      (is (= "ACME DENTAL" (:billing-provider claim)))
      (is (= "DOE"      (-> claim :subscriber :last)))
      (is (= "JANE"     (-> claim :subscriber :first)))
      (is (= "M00112233" (-> claim :subscriber :member-id))))
    (testing "service lines come through with procedure code stripped of qualifier"
      (is (= 1 (count (:service-lines claim))))
      (let [line (first (:service-lines claim))]
        (is (= "D1110"     (:procedure-code line)))
        (is (= 250.00      (:charge line)))
        (is (= 1           (:units line)))
        (is (= "20240515"  (:service-date line)))))))

(deftest all-service-lines-is-a-transducer
  (let [segs [{:id "SV3" :elements ["AD:D1110" "100.00" nil nil "1"]}
              {:id "CLM" :elements ["X" "1"]}
              {:id "SV3" :elements ["AD:D2150" "200.00" nil nil "2"]}]
        result (into [] core/all-service-lines segs)]
    (is (= 2 (count result)))
    (is (= "D1110" (-> result first :procedure-code)))
    (is (= "D2150" (-> result second :procedure-code)))
    (is (= 2 (-> result second :line-number)))))
