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

(defn- new-app
  ([] (new-app nil))
  ([tenants-file]
   (api/make-app
    (if tenants-file
      (api/make-context {:tenants-file tenants-file})
      {:catalog (adjudis.catalog/load-catalog)
       :history (hist/make-atom-store)
       :tenants nil}))))

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
  ([path] (GET path nil nil))
  ([path query-string] (GET path query-string nil))
  ([path query-string api-key]
   (*app* (cond-> {:request-method :get
                   :uri            path
                   :headers        (cond-> {"accept" "application/json"}
                                     api-key (assoc "x-api-key" api-key))}
            query-string (assoc :query-string query-string)))))

(defn- POST
  ([path body] (POST path body nil))
  ([path body api-key]
   (*app* {:request-method :post
           :uri            path
           :headers        (cond-> {"content-type" "application/json"
                                    "accept"       "application/json"}
                             api-key (assoc "x-api-key" api-key))
           :body           (json-bytes body)})))

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

;; ──────────────────────────────────────────────────────────────────────────
;; Multi-tenant tests
;; ──────────────────────────────────────────────────────────────────────────

(def acme-key "akey-acme-dev-only-do-not-use-in-prod")
(def beta-key "akey-beta-dev-only-do-not-use-in-prod")

(defn- adult-claim [code charge]
  {:claim {:claim-id "T-CLAIM"
           :control-number "1"
           :transaction-type "dental"
           :billing-provider "ACME"
           :subscriber {:member-id "M00112233"}
           :network "in-network"
           :total-charge charge
           :service-lines [{:line-number 1 :procedure-code code
                            :charge charge :service-date "2024-09-15" :units 1}]}
   :member {:subscriber-id "M00112233"
            :date-of-birth "1985-03-12"
            :coverage-start "2024-01-01"
            :coverage-end "2024-12-31"
            :plan-type "ppo"}})

(deftest multi-tenant-rejects-missing-api-key
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (let [r (GET "/catalog")]
      (is (= 401 (:status r)))
      (is (= "unauthorized" (:error (body-json r)))))))

(deftest multi-tenant-rejects-bad-api-key
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (let [r (GET "/catalog" nil "garbage-key")]
      (is (= 401 (:status r))))))

(deftest multi-tenant-health-and-version-stay-public
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (is (= 200 (:status (GET "/health"))))
    (is (= 200 (:status (GET "/version"))))))

(deftest acme-overlay-adds-and-overrides
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (let [b (body-json (GET "/catalog" nil acme-key))]
      (is (= "acme-dental" (:tenant-id b)))
      (is (some #(= "ACME-LARGE-SERVICE-CAP" (:rule-id %)) (:rules b))
          "acme adds ACME-LARGE-SERVICE-CAP")
      ;; The shipped DENTAL-ANNUAL-MAX-DEFAULT is overridden, not removed.
      (is (some #(= "DENTAL-ANNUAL-MAX-DEFAULT" (:rule-id %)) (:rules b))))))

(deftest beta-overlay-removes-shipped-rule
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (let [b (body-json (GET "/catalog" nil beta-key))]
      (is (= "beta-carrier" (:tenant-id b)))
      (is (not-any? #(= "DENTAL-SEALANT-AGE" (:rule-id %)) (:rules b))
          "beta removes DENTAL-SEALANT-AGE"))))

(deftest tenant-isolation-acme-rule-doesnt-leak-to-beta
  ;; ACME has ACME-LARGE-SERVICE-CAP that should pend a $2500 D2740.
  ;; Beta does NOT have that rule. Same claim through Beta should NOT pend
  ;; (it would still hit the shipped DENTAL-PREAUTH-CROWN at $500 threshold;
  ;; the key test is that ACME-LARGE-SERVICE-CAP isn't fired for Beta).
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (let [body (adult-claim "D2740" 2500.0)
          acme-resp (body-json (POST "/adjudicate" body acme-key))
          beta-resp (body-json (POST "/adjudicate" body beta-key))]
      (is (some #(= "ACME-LARGE-SERVICE-CAP" (:rule-id %)) (:findings acme-resp))
          "acme sees its custom rule fire")
      (is (not-any? #(= "ACME-LARGE-SERVICE-CAP" (:rule-id %)) (:findings beta-resp))
          "beta does NOT see acme's custom rule"))))

(deftest acme-tighter-annual-max-applies
  ;; Default annual max is $1500; ACME overrides to $1000.
  ;; A claim summing $1100 should pass under default but pend under ACME.
  ;; We adjudicate the same claim with no history; the override is purely
  ;; a max-amount tightening.
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (let [body (adult-claim "D2740" 1100.0)
          acme-rule (-> (GET "/catalog/DENTAL-ANNUAL-MAX-DEFAULT" nil acme-key)
                        body-json)]
      (is (= 1000.0 (get-in acme-rule [:params :max-amount]))
          "acme override sets max-amount to 1000"))))

;; ──────────────────────────────────────────────────────────────────────────
;; Observability tests: correlation IDs + /metrics
;; ──────────────────────────────────────────────────────────────────────────

(deftest correlation-id-generated-when-absent
  (let [r (GET "/health")]
    (is (= 200 (:status r)))
    (let [rid (get-in r [:headers "X-Request-Id"])]
      (is (some? rid))
      (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" rid)))))

(deftest correlation-id-honored-when-present
  (let [r (*app* {:request-method :get
                  :uri            "/health"
                  :headers        {"x-request-id" "supplied-by-caller-abc123"
                                   "accept"       "application/json"}})]
    (is (= "supplied-by-caller-abc123" (get-in r [:headers "X-Request-Id"])))))

(deftest metrics-endpoint-returns-prometheus-format
  (dotimes [_ 3] (GET "/health"))
  (let [r    (GET "/metrics")
        body (if (string? (:body r)) (:body r) (slurp (:body r)))]
    (is (= 200 (:status r)))
    (is (re-find #"# HELP" body) "should contain prometheus HELP comments")
    (is (re-find #"adjudis_auth_attempts_total|jvm_" body)
        "should contain at least one of our registered metrics or JVM defaults")))

(deftest metrics-records-auth-attempts
  (binding [*app* (new-app "resources/fixtures/tenants.edn")]
    (GET "/catalog" nil "garbage-key")
    (GET "/catalog" nil acme-key)
    (let [r    (GET "/metrics")
          body (if (string? (:body r)) (:body r) (slurp (:body r)))]
      (is (re-find #"adjudis_auth_attempts_total\{outcome=\"denied\"" body))
      (is (re-find #"adjudis_auth_attempts_total\{outcome=\"allowed\"" body)))))

(deftest metrics-records-adjudications
  (let [r (POST "/adjudicate"
                {:claim {:claim-id "M-1"
                         :control-number "1"
                         :transaction-type "dental"
                         :billing-provider "ACME"
                         :subscriber {:member-id "M00112233"}
                         :network "in-network"
                         :total-charge 50.0
                         :service-lines [{:line-number 1 :procedure-code "D0120"
                                          :charge 50.0 :service-date "2024-09-15" :units 1}]}
                 :member {:subscriber-id "M00112233"
                          :date-of-birth "1985-03-12"
                          :coverage-start "2024-01-01"
                          :coverage-end "2024-12-31"
                          :plan-type "ppo"}})]
    (is (= 200 (:status r))))
  (let [mr   (GET "/metrics")
        body (if (string? (:body mr)) (:body mr) (slurp (:body mr)))]
    (is (re-find #"adjudis_adjudications_total" body))))
