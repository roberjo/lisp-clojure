(ns edi.transform.cli
  "CLI: read EDN plists from stdin, emit one JSON claim per line on stdout.

   Wire it to project 02 like so:
     sbcl --script ../02-x12-parser/bin/emit-plist.lisp some.edi |
       clojure -M:run-cli"
  (:require [cheshire.core :as json]
            [clojure.edn   :as edn]
            [edi.transform.core   :as core]
            [edi.transform.schema :as schema])
  (:gen-class))

(defn read-all-edn [rdr]
  (let [eof (Object.)]
    (loop [out []]
      (let [v (edn/read {:eof eof} rdr)]
        (if (identical? v eof)
          out
          (recur (conj out v)))))))

(defn -main [& _args]
  (with-open [in (java.io.PushbackReader. *in*)]
    (doseq [tx (read-all-edn in)]
      (let [claim (core/transaction->claim tx)
            err   (schema/validate-claim claim)]
        (when err
          (binding [*out* *err*]
            (println "WARN: claim failed validation:" (pr-str err))))
        (println (json/generate-string claim))))))
