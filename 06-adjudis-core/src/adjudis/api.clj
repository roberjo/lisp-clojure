(ns adjudis.api
  "Reitit-ring routes + handlers. Stateless: the catalog and history store are
   injected via the ring request map so handlers stay pure-ish.

   Wire shape mirrors the CLI: JSON in, JSON out, same field names. The
   architectural pun is that the engine doesn't know it's behind HTTP."
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [adjudis.engine   :as engine]
            [adjudis.shadow   :as shadow]
            [adjudis.catalog  :as catalog]
            [adjudis.schema   :as schema]
            [adjudis.versions :as versions]
            [adjudis.history  :as hist]))

;; ──────────────────────────────────────────────────────────────────────────
;; Context — injected by the server, read by handlers
;; ──────────────────────────────────────────────────────────────────────────

(defn make-context
  "Build the immutable per-request context the routes will close over.
   In production this would hold a per-tenant accessor; today it's a single
   global catalog + one in-process history store."
  []
  {:catalog (catalog/load-catalog)
   :history (hist/make-atom-store)})

(defn- ctx [request] (::context request))

;; ──────────────────────────────────────────────────────────────────────────
;; Handlers
;; ──────────────────────────────────────────────────────────────────────────

(defn health-handler [_]
  {:status 200 :body {:status "ok"}})

(defn version-handler [_]
  {:status 200
   :body   {:engine-version  schema/engine-version
            :catalog-version schema/catalog-version}})

(defn catalog-list-handler [request]
  (let [c       (:catalog (ctx request))
        as-of   (get-in request [:query-params "as-of"])
        active  (if as-of (versions/active-rules c as-of) c)]
    {:status 200
     :body   {:count        (count active)
              :as-of        as-of
              :rules        (mapv #(select-keys % [:rule-id :category :severity
                                                    :description :reason-code
                                                    :effective-from :effective-to])
                                  active)}}))

(defn catalog-rule-handler [request]
  (let [rule-id (get-in request [:path-params :rule-id])
        rule    (versions/rule-by-id (:catalog (ctx request)) rule-id)]
    (if rule
      {:status 200 :body rule}
      {:status 404 :body {:error "rule-not-found" :rule-id rule-id}})))

(defn- require-keys [m ks]
  (let [missing (remove #(contains? m %) ks)]
    (when (seq missing)
      (throw (ex-info "missing required fields"
                      {:type :validation :missing (vec missing)})))))

(defn adjudicate-handler [request]
  (let [body    (:body-params request)
        _       (require-keys body [:claim :member])
        {:keys [claim member as-of]} body
        context (ctx request)
        decision (engine/adjudicate claim member
                                    {:catalog (:catalog context)
                                     :history (:history context)
                                     :as-of   as-of})]
    {:status 200 :body decision}))

(defn shadow-handler [request]
  (let [body    (:body-params request)
        _       (require-keys body [:claim :member :proposed-catalog])
        {:keys [claim member proposed-catalog as-of]} body
        context (ctx request)
        result  (shadow/adjudicate-shadow claim member
                                          {:catalog (:catalog context)
                                           :history (:history context)
                                           :proposed-catalog proposed-catalog
                                           :as-of as-of})]
    {:status 200 :body result}))

;; ──────────────────────────────────────────────────────────────────────────
;; Error handling: turn ex-info {:type :validation ...} into 400
;; ──────────────────────────────────────────────────────────────────────────

(defn- validation-exception-handler [^Exception e _request]
  {:status 400
   :body   {:error   "validation"
            :message (.getMessage e)
            :details (ex-data e)}})

(def exception-middleware
  (exception/create-exception-middleware
   (merge exception/default-handlers
          {;; map our ex-info :type to handlers
           ::exception/wrap
           (fn [handler ^Exception e request]
             (let [data (ex-data e)]
               (if (= :validation (:type data))
                 (validation-exception-handler e request)
                 (handler e request))))})))

;; ──────────────────────────────────────────────────────────────────────────
;; Router
;; ──────────────────────────────────────────────────────────────────────────

(defn router []
  (ring/router
   [["/health"  {:get  {:handler health-handler}}]
    ["/version" {:get  {:handler version-handler}}]
    ["/catalog"
     ["" {:get {:handler catalog-list-handler}}]
     ["/:rule-id" {:get {:handler catalog-rule-handler}}]]
    ["/adjudicate" {:post {:handler adjudicate-handler}}]
    ["/shadow"     {:post {:handler shadow-handler}}]]
   {:data {:muuntaja   m/instance
           :middleware [parameters/parameters-middleware
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-response-middleware
                        exception-middleware
                        muuntaja/format-request-middleware]}}))

(defn make-app
  "Compose the ring handler. context is injected into every request under
   the ::context key so handlers can pull catalog/history out without
   accessing globals."
  [context]
  (let [base (ring/ring-handler
              (router)
              (ring/create-default-handler))]
    (fn [request]
      (base (assoc request ::context context)))))
