(ns adjudis.api
  "Reitit-ring routes + handlers. Stateless: the catalog, history store, and
   tenant registry are injected via the ring request map so handlers stay
   pure-ish.

   Wire shape mirrors the CLI: JSON in, JSON out, same field names. The
   architectural pun is that the engine doesn't know it's behind HTTP, and
   doesn't know which tenant it's serving — the handler computes the
   tenant's effective catalog and hands it to the engine."
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
            [adjudis.history  :as hist]
            [adjudis.tenants  :as tenants]))

;; ──────────────────────────────────────────────────────────────────────────
;; Context — injected by the server, read by handlers
;; ──────────────────────────────────────────────────────────────────────────

(defn make-context
  "Build the immutable per-request context the routes will close over.

   - :catalog is the SHIPPED base catalog
   - :history is a single in-process store (Phase 2 swap to XTDB will keep
     this signature)
   - :tenants is the loaded tenant map (id → tenant record), or nil/{} for
     single-tenant mode
   - :tenants-base-dir is where :overlay-file paths resolve from"
  ([] (make-context {}))
  ([{:keys [tenants-file]}]
   {:catalog          (catalog/load-catalog)
    :history          (hist/make-atom-store)
    :tenants          (when tenants-file (tenants/load-tenants tenants-file))
    :tenants-base-dir (when tenants-file
                        (.getParent (java.io.File. ^String tenants-file)))}))

(defn- ctx [request] (::context request))

(defn- request-tenant [request] (::tenant request))

(defn- effective-catalog-for [request]
  (let [c (ctx request)
        base (:catalog c)
        t    (request-tenant request)]
    (if t
      (tenants/effective-catalog base t (:tenants-base-dir c))
      base)))

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
  (let [c       (effective-catalog-for request)
        as-of   (get-in request [:query-params "as-of"])
        active  (if as-of (versions/active-rules c as-of) c)
        tid     (:tenant-id (request-tenant request))]
    {:status 200
     :body   (cond-> {:count (count active)
                      :as-of as-of
                      :rules (mapv #(select-keys % [:rule-id :category :severity
                                                     :description :reason-code
                                                     :effective-from :effective-to])
                                   active)}
               tid (assoc :tenant-id tid))}))

(defn catalog-rule-handler [request]
  (let [rule-id (get-in request [:path-params :rule-id])
        rule    (versions/rule-by-id (effective-catalog-for request) rule-id)]
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
                                    {:catalog (effective-catalog-for request)
                                     :history (:history context)
                                     :as-of   as-of})
        tid     (:tenant-id (request-tenant request))]
    {:status 200 :body (cond-> decision tid (assoc :tenant-id tid))}))

(defn shadow-handler [request]
  (let [body    (:body-params request)
        _       (require-keys body [:claim :member :proposed-catalog])
        {:keys [claim member proposed-catalog as-of]} body
        context (ctx request)
        result  (shadow/adjudicate-shadow claim member
                                          {:catalog (effective-catalog-for request)
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
;; API key auth middleware
;; ──────────────────────────────────────────────────────────────────────────

(defn- wrap-tenant-auth
  "If the deployment has a tenants registry, every request to a protected
   route must carry a valid X-API-Key header. The resolved tenant is attached
   to the request under ::tenant. In single-tenant deployments (no
   tenants registry), this middleware is a no-op.

   Multi-tenant deployments require auth on /catalog, /adjudicate, /shadow.
   /health and /version stay public (load balancers + ops dashboards need
   them without credentials)."
  [handler]
  (fn [request]
    (let [registry (:tenants (ctx request))]
      (if (empty? registry)
        (handler request)
        (let [api-key (get-in request [:headers "x-api-key"])
              tenant  (tenants/lookup-by-api-key registry api-key)]
          (if tenant
            (handler (assoc request ::tenant tenant))
            {:status 401
             :body   {:error   "unauthorized"
                      :message "missing or invalid X-API-Key"}}))))))

;; ──────────────────────────────────────────────────────────────────────────
;; Router
;; ──────────────────────────────────────────────────────────────────────────

(defn router []
  (ring/router
   [;; Public — load-balancer / ops dashboards
    ["/health"  {:get {:handler health-handler}}]
    ["/version" {:get {:handler version-handler}}]

    ;; Tenant-scoped — auth required if a tenants registry is configured
    ["" {:middleware [wrap-tenant-auth]}
     ["/catalog"
      ["" {:get {:handler catalog-list-handler}}]
      ["/:rule-id" {:get {:handler catalog-rule-handler}}]]
     ["/adjudicate" {:post {:handler adjudicate-handler}}]
     ["/shadow"     {:post {:handler shadow-handler}}]]]
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
