(ns adjudis.logging
  "Thin wrappers around tools.logging that ensure every log line carries a
   correlation-id (request_id) and other MDC context we set per-request.

   tools.logging gives us level dispatch; logback (via SLF4J) does the actual
   formatting via logstash-logback-encoder, configured in resources/logback.xml."
  (:require [clojure.tools.logging :as log])
  (:import [org.slf4j MDC]))

(defn put-mdc [k v]
  (when (and k v) (MDC/put (name k) (str v))))

(defn remove-mdc [k]
  (when k (MDC/remove (name k))))

(defn clear-mdc [] (MDC/clear))

(defmacro with-context
  "Bind MDC keys for the duration of body. Ensures keys are removed even on
   exception. Accepts a map of {:keyword \"value\" ...}."
  [ctx & body]
  `(let [keys# (keys ~ctx)]
     (try
       (doseq [[k# v#] ~ctx] (put-mdc k# v#))
       ~@body
       (finally
         (doseq [k# keys#] (remove-mdc k#))))))

(defn info  [msg & {:as fields}] (log/info  (assoc fields :msg msg)))
(defn warn  [msg & {:as fields}] (log/warn  (assoc fields :msg msg)))
(defn error [msg & {:as fields}] (log/error (assoc fields :msg msg)))

;; The audit logger is a sibling, routed through logback's "audit" name.
(def ^:private audit-logger
  (org.slf4j.LoggerFactory/getLogger "audit"))

(defn audit
  "Emit an audit event. Fields are folded into the structured payload by
   tools.logging's printing of the map; logback's logstash encoder will
   serialize each as a top-level JSON field.

   Required fields by convention: :actor :action :outcome.
   Always also include whatever identifiers help the auditor follow the
   trail (claim-id, rule-id, etc)."
  [event & {:as fields}]
  (let [payload (assoc fields :event event)]
    (.info audit-logger (pr-str payload))))
