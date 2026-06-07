(ns adjudis.metrics
  "Prometheus metrics for the adjudis HTTP server. One registry owned by this
   namespace; handlers and middleware bump counters / observe histograms.

   The /metrics endpoint is plumbed into the Reitit router by adjudis.api."
  (:require [iapetos.core :as prom]
            [iapetos.collector.jvm :as jvm]
            [iapetos.collector.ring :as ring-metrics]))

(defonce registry
  (-> (prom/collector-registry)
      (jvm/initialize)
      ;; HTTP request counter (provided by iapetos ring collector).
      (ring-metrics/initialize)
      ;; Adjudication-specific instruments.
      (prom/register
       (prom/counter   :adjudis/auth-attempts-total
                       {:description "API key auth attempts."
                        :labels      [:outcome]})
       (prom/counter   :adjudis/adjudications-total
                       {:description "Claims adjudicated."
                        :labels      [:tenant :verdict]})
       (prom/counter   :adjudis/findings-emitted-total
                       {:description "Findings emitted per rule category."
                        :labels      [:category :severity]})
       (prom/histogram :adjudis/adjudication-duration-seconds
                       {:description "Time to adjudicate a single claim."
                        :labels      [:tenant]
                        :buckets     [0.005 0.01 0.025 0.05 0.1 0.25 0.5 1 2.5]}))))

;; Convenience wrappers — explicit functions are nicer to grep for than
;; ad-hoc (prom/inc registry :foo) calls scattered across handlers.

(defn record-auth-attempt! [outcome]
  (prom/inc registry :adjudis/auth-attempts-total {:outcome (name outcome)}))

(defn record-adjudication! [tenant verdict duration-seconds]
  (prom/inc registry :adjudis/adjudications-total
            {:tenant (or tenant "single") :verdict (name verdict)})
  (prom/observe registry :adjudis/adjudication-duration-seconds
                {:tenant (or tenant "single")} duration-seconds))

(defn record-findings! [findings]
  (doseq [f findings]
    (prom/inc registry :adjudis/findings-emitted-total
              {:category (name (or (:rule-category f) "unknown"))
               :severity (name (or (:severity f) "unknown"))})))

(defn scrape
  "Render the registry in Prometheus text exposition format."
  []
  (iapetos.export/text-format registry))
