(ns adjudis.cli
  "CLI: read normalized claim JSON on stdin (one per line, or single object),
   adjudicate against the loaded catalog, emit a decision JSON per line on
   stdout.

   Member and history are loaded from EDN fixtures specified via flags:
     --member  path/to/member.edn
     --history path/to/history.edn

   This is the MVP. Phase 3 replaces this with an HTTP API."
  (:require [cheshire.core :as json]
            [clojure.edn   :as edn]
            [clojure.java.io :as io]
            [adjudis.engine  :as engine]
            [adjudis.history :as hist])
  (:gen-class))

(defn- read-edn-file [path]
  (with-open [r (java.io.PushbackReader. (io/reader path))]
    (edn/read r)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (case (first args)
      "--member"  (recur (drop 2 args) (assoc opts :member-path  (second args)))
      "--history" (recur (drop 2 args) (assoc opts :history-path (second args)))
      "--help"    (assoc opts :help true)
      nil         opts
      (recur (rest args) opts))))

(defn- read-claims [rdr]
  (let [eof (Object.)]
    (loop [out []]
      (let [line (.readLine rdr)]
        (cond
          (nil? line) out
          (clojure.string/blank? line) (recur out)
          :else (recur (conj out (json/parse-string line true))))))))

(defn- read-json-lines-or-single [rdr]
  "Accept either JSON Lines (one claim per line) or one big JSON object."
  (let [content (slurp rdr)
        trimmed (clojure.string/trim content)]
    (cond
      (clojure.string/blank? trimmed) []
      (.startsWith trimmed "[")
      (json/parse-string trimmed true)
      :else
      (->> (clojure.string/split-lines trimmed)
           (remove clojure.string/blank?)
           (mapv #(json/parse-string % true))))))

(defn -main [& args]
  (let [{:keys [member-path history-path help]} (parse-args args)]
    (when help
      (println "Usage: adjudis.cli --member member.edn [--history history.edn]")
      (System/exit 0))
    (when-not member-path
      (binding [*out* *err*]
        (println "ERROR: --member is required"))
      (System/exit 2))
    (let [member  (read-edn-file member-path)
          history (if history-path
                    (hist/load-fixture history-path)
                    (hist/make-atom-store))
          claims  (read-json-lines-or-single *in*)]
      (doseq [claim claims]
        (let [decision (engine/adjudicate claim member {:history history})]
          (println (json/generate-string decision)))))))
