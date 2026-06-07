(ns adjudis.author
  "Rule-author CLI: tooling that lets a non-engineer maintain the rule catalog
   safely. Three commands:

     validate    — schema-check a catalog (or proposed file); list issues.
     dry-run     — adjudicate a fixture claim against a catalog (or proposed
                   catalog) and print the decision. The 'will this rule do
                   what I think it does?' loop.
     diff        — compare two catalogs by rule-id (added/removed/changed).
     shadow      — adjudicate a fixture claim under current vs proposed catalog
                   and print the delta.

   Usage:
     clojure -M:author validate
     clojure -M:author validate --catalog path/to/proposed/
     clojure -M:author dry-run  --claim claim.json --member member.edn
     clojure -M:author diff     --before path/   --after  path/
     clojure -M:author shadow   --claim claim.json --member member.edn \\
                                 --proposed path/"
  (:require [cheshire.core :as json]
            [clojure.edn   :as edn]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [adjudis.catalog  :as catalog]
            [adjudis.engine   :as engine]
            [adjudis.history  :as hist]
            [adjudis.shadow   :as shadow]
            [adjudis.versions :as versions])
  (:gen-class))

;; ── catalog loading by path (vs. classpath) ───────────────────────────────

(defn- read-edn [path]
  (with-open [r (java.io.PushbackReader. (io/reader path))]
    (edn/read r)))

(defn- load-catalog-from-dir
  "Load every *.edn file in dir as a catalog. Order is filename-sorted for
   deterministic diffs."
  [dir]
  (let [files (->> (file-seq (io/file dir))
                   (filter #(and (.isFile %)
                                 (str/ends-with? (.getName %) ".edn")))
                   sort)]
    (vec (mapcat read-edn files))))

;; ── schema validation ─────────────────────────────────────────────────────

(def ^:private required-keys #{:rule-id :category :description :severity :reason-code :params})
(def ^:private known-categories
  #{:frequency-limit :age-appropriate :pre-auth-required
    :eligibility :annual-maximum :fee-schedule})
(def ^:private known-severities #{:deny :pending :adjust :warn :inform})

(defn- validate-rule [rule]
  (let [missing (clojure.set/difference required-keys (set (keys rule)))
        issues  (cond-> []
                  (seq missing)
                  (conj (str "missing required keys: " (vec missing)))

                  (and (:category rule)
                       (not (known-categories (:category rule))))
                  (conj (str "unknown category: " (:category rule)
                             " (known: " known-categories ")"))

                  (and (:severity rule)
                       (not (known-severities (:severity rule))))
                  (conj (str "unknown severity: " (:severity rule)
                             " (known: " known-severities ")"))

                  (not (map? (:params rule)))
                  (conj "params must be a map"))]
    (when (seq issues) {:rule-id (:rule-id rule) :issues issues})))

(defn- find-duplicate-ids [catalog]
  (->> (map :rule-id catalog)
       frequencies
       (filter #(> (val %) 1))
       (mapv key)))

(defn validate-catalog [catalog]
  (let [per-rule (keep validate-rule catalog)
        dupes    (find-duplicate-ids catalog)]
    {:rule-count        (count catalog)
     :rules-with-issues per-rule
     :duplicate-ids     dupes
     :ok?               (and (empty? per-rule) (empty? dupes))}))

(defn- print-validation [result]
  (println (format "Loaded %d rules." (:rule-count result)))
  (if (:ok? result)
    (println "OK — no issues.")
    (do
      (doseq [{:keys [rule-id issues]} (:rules-with-issues result)]
        (println (format "  %s:" (or rule-id "(no rule-id)")))
        (doseq [i issues] (println (format "    - %s" i))))
      (when (seq (:duplicate-ids result))
        (println "  duplicate rule-ids: " (:duplicate-ids result))))))

;; ── arg parsing ───────────────────────────────────────────────────────────

(defn- parse-flags [args]
  (loop [args args opts {}]
    (case (first args)
      "--catalog"   (recur (drop 2 args) (assoc opts :catalog-path  (second args)))
      "--proposed"  (recur (drop 2 args) (assoc opts :proposed-path (second args)))
      "--before"    (recur (drop 2 args) (assoc opts :before-path   (second args)))
      "--after"     (recur (drop 2 args) (assoc opts :after-path    (second args)))
      "--claim"     (recur (drop 2 args) (assoc opts :claim-path    (second args)))
      "--member"    (recur (drop 2 args) (assoc opts :member-path   (second args)))
      "--history"   (recur (drop 2 args) (assoc opts :history-path  (second args)))
      "--as-of"     (recur (drop 2 args) (assoc opts :as-of         (second args)))
      nil           opts
      (recur (rest args) opts))))

(defn- need [opts k usage]
  (or (get opts k)
      (do (binding [*out* *err*] (println (str "ERROR: missing " (name k))) (println usage))
          (System/exit 2))))

;; ── commands ──────────────────────────────────────────────────────────────

(defn cmd-validate [opts]
  (let [catalog (if-let [p (:catalog-path opts)]
                  (load-catalog-from-dir p)
                  (catalog/load-catalog))
        result  (validate-catalog catalog)]
    (print-validation result)
    (System/exit (if (:ok? result) 0 1))))

(defn cmd-dry-run [opts]
  (let [claim   (json/parse-string (slurp (need opts :claim-path  "needs --claim")) true)
        member  (read-edn       (need opts :member-path "needs --member"))
        history (if-let [p (:history-path opts)] (hist/load-fixture p) (hist/make-atom-store))
        catalog (if-let [p (:catalog-path opts)] (load-catalog-from-dir p) (catalog/load-catalog))
        decision (engine/adjudicate claim member
                                    {:history history :catalog catalog
                                     :as-of (:as-of opts)})]
    (println (json/generate-string decision {:pretty true}))))

(defn cmd-diff [opts]
  (let [before (load-catalog-from-dir (need opts :before-path "needs --before"))
        after  (load-catalog-from-dir (need opts :after-path  "needs --after"))
        d      (versions/diff-catalogs before after)]
    (println (format "added:   %d" (count (:added d))))
    (doseq [r (:added d)]   (println (format "  + %s  (%s)" (:rule-id r) (:category r))))
    (println (format "removed: %d" (count (:removed d))))
    (doseq [r (:removed d)] (println (format "  - %s  (%s)" (:rule-id r) (:category r))))
    (println (format "changed: %d" (count (:changed d))))
    (doseq [c (:changed d)] (println (format "  ~ %s" (:rule-id c))))))

(defn cmd-shadow [opts]
  (let [claim    (json/parse-string (slurp (need opts :claim-path  "needs --claim")) true)
        member   (read-edn       (need opts :member-path "needs --member"))
        history  (if-let [p (:history-path opts)] (hist/load-fixture p) (hist/make-atom-store))
        current  (if-let [p (:catalog-path opts)] (load-catalog-from-dir p) (catalog/load-catalog))
        proposed (load-catalog-from-dir (need opts :proposed-path "needs --proposed"))
        result   (shadow/adjudicate-shadow claim member
                                           {:history history
                                            :catalog current
                                            :proposed-catalog proposed
                                            :as-of (:as-of opts)})]
    (println "─── current ───")
    (println (format "verdict: %s, provider-payment: $%.2f"
                     (name (get-in result [:current :verdict]))
                     (get-in result [:current :provider-payment])))
    (println "─── proposed ───")
    (println (format "verdict: %s, provider-payment: $%.2f"
                     (name (get-in result [:proposed :verdict]))
                     (get-in result [:proposed :provider-payment])))
    (println "─── delta ───")
    (println (format "verdict-change: %s" (name (get-in result [:delta :verdict-change]))))
    (println (format "dollars-delta:  $%.2f" (get-in result [:delta :dollars])))
    (when (seq (get-in result [:delta :rules-added]))
      (println (format "rules-added:   %s" (vec (get-in result [:delta :rules-added])))))
    (when (seq (get-in result [:delta :rules-removed]))
      (println (format "rules-removed: %s" (vec (get-in result [:delta :rules-removed])))))))

(defn -main [& args]
  (let [cmd  (first args)
        opts (parse-flags (rest args))]
    (case cmd
      "validate" (cmd-validate opts)
      "dry-run"  (cmd-dry-run  opts)
      "diff"     (cmd-diff     opts)
      "shadow"   (cmd-shadow   opts)
      (do (binding [*out* *err*]
            (println "Usage:")
            (println "  adjudis.author validate [--catalog DIR]")
            (println "  adjudis.author dry-run  --claim FILE --member FILE [--catalog DIR] [--history FILE] [--as-of DATE]")
            (println "  adjudis.author diff     --before DIR --after DIR")
            (println "  adjudis.author shadow   --claim FILE --member FILE --proposed DIR [--catalog DIR]"))
          (System/exit 2)))))
