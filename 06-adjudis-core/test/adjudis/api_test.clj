(ns adjudis.api-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as json]
            [adjudis.api :as api]
            [adjudis.history :as hist])
  (:import [java.io ByteArrayInputStream]))

;; ──────────────────────────────────────────────────────────────────────────
;; Test fixtures
;; ──────────────────────────────────────────────────────────────────────────

(def ^:dynamic *app* nil)

(defn- new-app []
  (api/make-app
   {:catalog (adjudis.catalog/load-catalog)
    :history (hist/make-atom-store)}))

(use-fixtures :each
  (fn [f]
    (binding [*app* (new-app)]
      (f))))

;; ──────────────────────────────────────────────────────────────────────────
;; Ring-mock-style request helpers
;; ──────────────────────────────────────────────────────────────────────────

(defn- json-bytes [data]
  (ByteArrayInputStream. (.getBytes (json/generate-string data) "UTF-8")))

(defn- GET
  ([path] (GET path nil))
  ([path query-string]
   (*app* (cond-> {:request-method :get
                   :uri            path
                   :headers        {"accept" "application/json"}}
            query-string (assoc :query-string query-string)))))

(defn- POST [path body]
  (*app* {:request-method :post
          :uri            path
          :headers        {"content-type" "application/json"
                           "accept"       "application/json"}
          :body           (json-bytes body)}))

(defn- body-json [response]
  ;; muuntaja already decoded the body to data when content-type negotiated
  (let [b (:body response)]
    (cond
      (map? b)     b
      (vector? b)  b
      (string? b)  (json/parse-string b true)
      (nil? b)     nil
      :else        (json/parse-string (slurp b) true))))

;; ──────────────────────────────────────────────────────────────────────────
;; Tests
;; ──────────────────────────────────────────────────────────────────────────

(deftest health-returns-200
  (let [r (GET "/health")]
    (is (= 200 (:status r)))
    (is (= "ok" (:status (body-json r))))))

(deftest version-returns-engine-and-catalog
  (let [r (GET "/version")
        b (body-json r)]
    (is (= 200 (:status r)))
    (is (some? (:engine-version b)))
    (is (some? (:catalog-version b)))))

(deftest catalog-list-returns-all-rules
  (let [r (GET "/catalog")
        b (body-json r)]
    (is (= 200 (:status r)))
    (is (pos? (:count b)))
    (is (every? :rule-id (:rules b)))))

(deftest catalog-list-respects-as-of-when-rules-versioned
  ;; All shipped rules are unversioned, so they're always active regardless of as-of.
  (let [r (GET "/catalog" "as-of=2024-06-15")
        b (body-json r)]
    (is (= 200 (:status r)))
    (is (pos? (:count b)))
    (is (= "2024-06-15" (:as-of b)))))

(deftest catalog-rule-by-id-200-and-404
  (let [r (GET "/catalog/DENTAL-PROPHY-ADULT-FREQ")]
    (is (= 200 (:status r)))
    (is (= "DENTAL-PROPHY-ADULT-FREQ" (:rule-id (body-json r)))))
  (let [r (GET "/catalog/NO-SUCH-RULE")]
    (is (= 404 (:status r)))
    (is (= "rule-not-found" (:error (body-json r))))))

(deftest adjudicate-pays-clean-claim
  (let [r (POST "/adjudicate"
                {:claim {:claim-id "API-1"
                         :control-number "1"
                         :transaction-type "dental"
                         :billing-provider "ACME"
                         :subscriber {:member-id "M00112233"}
                         :network "in-network"
                         :total-charge 50.0
                         :service-lines [{:line-number 1
                                          :procedure-code "D0120"
                                          :charge 50.0
                                          :service-date "2024-09-15"
                                          :units 1}]}
                 :member {:subscriber-id "M00112233"
                          :date-of-birth "1985-03-12"
                          :coverage-start "2024-01-01"
                          :coverage-end "2024-12-31"
                          :plan-type "ppo"}})
        b (body-json r)]
    (is (= 200 (:status r)))
    (is (= "paid" (:verdict b)))))

(deftest adjudicate-missing-required-fields-returns-400
  (let [r (POST "/adjudicate" {:claim {}})  ;; no :member
        b (body-json r)]
    (is (= 400 (:status r)))
    (is (= "validation" (:error b)))
    (is (some #{"member"} (mapv name (:missing (:details b)))))))

(deftest shadow-endpoint-returns-current-and-proposed
  (let [tighter [{:rule-id "FREQ-TIGHT"
                  :category "frequency-limit"
                  :description "1/year"
                  :severity "deny"
                  :reason-code "FREQ"
                  :params {:procedure-codes ["D1110"] :max-per-year 0}
                  :citation {:source "test"}}]
        r (POST "/shadow"
                {:claim {:claim-id "API-2"
                         :control-number "1"
                         :transaction-type "dental"
                         :billing-provider "ACME"
                         :subscriber {:member-id "M00112233"}
                         :network "in-network"
                         :total-charge 100.0
                         :service-lines [{:line-number 1 :procedure-code "D1110"
                                          :charge 100.0 :service-date "2024-09-15" :units 1}]}
                 :member {:subscriber-id "M00112233"
                          :date-of-birth "1985-03-12"
                          :coverage-start "2024-01-01"
                          :coverage-end "2024-12-31"
                          :plan-type "ppo"}
                 :proposed-catalog tighter})
        b (body-json r)]
    (is (= 200 (:status r)))
    (is (contains? b :current))
    (is (contains? b :proposed))
    (is (contains? b :delta))))

(deftest unknown-route-404
  (let [r (*app* {:request-method :get :uri "/nope"})]
    (is (= 404 (:status r)))))
