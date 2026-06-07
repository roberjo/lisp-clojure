(ns adjudis.server
  "Embedded Jetty entry point. Reads PORT from env (default 8080),
   constructs the request context once, starts the server, prints the
   bound port + version on stdout for the orchestrator to capture, and
   blocks forever."
  (:require [ring.adapter.jetty :as jetty]
            [adjudis.api    :as api]
            [adjudis.schema :as schema])
  (:gen-class))

(defn- env-port []
  (Integer/parseInt (or (System/getenv "PORT") "8080")))

(defn start
  "Construct the handler with a fresh context and start Jetty. Returns the
   Server instance so callers (tests) can stop it.

   When env var TENANTS_FILE is set, the deployment runs in multi-tenant mode
   and protected routes (/catalog, /adjudicate, /shadow) require a valid
   X-API-Key header. Otherwise it's single-tenant and auth is bypassed."
  ([] (start (env-port)))
  ([port]
   (let [tenants-file (System/getenv "TENANTS_FILE")
         context (api/make-context (cond-> {}
                                     tenants-file (assoc :tenants-file tenants-file)))
         app     (api/make-app context)]
     (jetty/run-jetty app {:port port :join? false}))))

(defn -main [& _]
  (let [port (env-port)
        srv  (start port)
        mode (if (System/getenv "TENANTS_FILE") "multi-tenant" "single-tenant")]
    (println (format "adjudis %s listening on port %d (catalog %s, mode: %s)"
                     schema/engine-version port schema/catalog-version mode))
    (.join srv)))
