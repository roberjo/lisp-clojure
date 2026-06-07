(ns adjudis.catalog
  "Load all *.edn files under resources/rule-catalog/ and expose them as a
   flat list of rule maps. The engine inserts these as Clara facts so the
   productions can pattern-match against them.

   This is the architectural punchline: rule PRODUCTIONS (one per category)
   are code; rule INSTANCES (specific D1110-limited-to-2/year) are data."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private catalog-files
  "Explicit list of catalog files. New files must be registered here.
   Trade-off: a directory walk is more convenient, but walking io/resource
   URLs is fragile across filesystem vs. jar classpath entries. Explicit
   registration is reliable and self-documenting."
  ["rule-catalog/frequency.edn"
   "rule-catalog/age-appropriate.edn"
   "rule-catalog/annual-max.edn"
   "rule-catalog/fee-schedule.edn"
   "rule-catalog/pre-auth.edn"
   "rule-catalog/eligibility.edn"])

(defn- read-edn-resource [path]
  (let [url (io/resource path)]
    (when-not url
      (throw (ex-info (str "Catalog file not found on classpath: " path)
                      {:path path})))
    (with-open [r (java.io.PushbackReader. (io/reader url))]
      (edn/read r))))

(defn load-catalog
  "Return a flat vector of rule maps from every registered catalog file."
  []
  (vec (mapcat read-edn-resource catalog-files)))

(defn group-by-category [catalog]
  (group-by :category catalog))

(defn find-rule [catalog rule-id]
  (some #(when (= rule-id (:rule-id %)) %) catalog))
