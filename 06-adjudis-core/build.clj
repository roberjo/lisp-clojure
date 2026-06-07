(ns build
  "tools.build script for producing an uberjar.

   Invoke from the project root:
       clojure -T:build uber
       clojure -T:build clean"
  (:require [clojure.tools.build.api :as b]))

(def lib       'adjudis/adjudis-core)
(def version   "0.1.0")
(def class-dir "target/classes")
(def basis     (delay (b/create-basis {:project "deps.edn"})))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (println "cleaned target/"))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  ;; Non-AOT uberjar. AOT-compiling Clara productions generates thousands of
  ;; inner classes for the alpha/beta network and is *very* slow (~10+ min
  ;; locally). Non-AOT trades ~10s of additional cold start for a 30-second
  ;; build. The startup cost is paid once per container restart and is
  ;; dominated by Clara's session compile anyway.
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'clojure.main})
  (println (format "built %s" uber-file)))
