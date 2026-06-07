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
   Server instance so callers (tests) can stop it."
  ([] (start (env-port)))
  ([port]
   (let [context (api/make-context)
         app     (api/make-app context)]
     (jetty/run-jetty app {:port port :join? false}))))

(defn -main [& _]
  (let [port (env-port)
        srv  (start port)]
    (println (format "adjudis %s listening on port %d (catalog %s)"
                     schema/engine-version port schema/catalog-version))
    (.join srv)))
