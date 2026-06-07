(ns adjudis.bench
  "Throughput / latency benchmark for the adjudication engine. Sized to be
   run from the CLI:

       clojure -M:bench

   Each scenario runs N adjudications back-to-back and reports mean +
   p50/p95/p99 latency. No criterium dependency — we use System/nanoTime
   so the benchmark itself doesn't add deps."
  (:require [adjudis.engine        :as engine]
            [adjudis.history       :as hist]
            [adjudis.catalog       :as catalog]
            [adjudis.catalog-synth :as synth])
  (:gen-class))

(defn- sample-member []
  {:subscriber-id  "BENCH-MEM-1"
   :date-of-birth  "1985-03-12"
   :coverage-start "2024-01-01"
   :coverage-end   "2024-12-31"
   :plan-type      :ppo})

(defn- sample-claim [i]
  {:claim-id          (str "BENCH-" i)
   :control-number    (str i)
   :transaction-type  :dental
   :billing-provider  "ACME DENTAL"
   :subscriber        {:member-id "BENCH-MEM-1"}
   :network           :in-network
   :total-charge      150.0
   :service-lines     [{:line-number 1 :procedure-code "D1110"
                        :charge 150.0 :service-date "2024-06-15" :units 1}]})

(defn- percentile [sorted-arr p]
  (let [n (count sorted-arr)
        idx (int (Math/floor (* (/ p 100.0) (dec n))))]
    (nth sorted-arr idx)))

(defn- ns->ms [ns] (/ ns 1e6))

(defn benchmark
  "Adjudicate iterations claims against catalog; report timing stats."
  [catalog iterations]
  (let [member (sample-member)
        history (hist/make-atom-store)
        ;; warm-up: 5 runs to let JIT and Clara session caching settle.
        _ (dotimes [i 5]
            (engine/adjudicate (sample-claim i) member {:catalog catalog :history history}))
        times (vec (for [i (range iterations)]
                     (let [t0 (System/nanoTime)
                           _  (engine/adjudicate (sample-claim i) member
                                                 {:catalog catalog :history history})
                           t1 (System/nanoTime)]
                       (- t1 t0))))
        sorted (sort times)]
    {:catalog-size (count catalog)
     :iterations   iterations
     :mean-ms      (ns->ms (/ (reduce + 0 times) iterations))
     :p50-ms       (ns->ms (percentile sorted 50))
     :p95-ms       (ns->ms (percentile sorted 95))
     :p99-ms       (ns->ms (percentile sorted 99))
     :max-ms       (ns->ms (last sorted))}))

(defn- print-row [label result]
  (println (format "%-25s | rules=%4d | iters=%4d | mean=%6.2f ms | p50=%6.2f | p95=%6.2f | p99=%6.2f | max=%6.2f"
                   label
                   (:catalog-size result)
                   (:iterations result)
                   (:mean-ms result)
                   (:p50-ms result)
                   (:p95-ms result)
                   (:p99-ms result)
                   (:max-ms result))))

(defn -main [& _]
  (println "Adjudis adjudication-engine benchmark")
  (println "=====================================")
  (let [shipped (catalog/load-catalog)]
    (print-row "shipped catalog"       (benchmark shipped              200))
    (print-row "synthetic 50 rules"    (benchmark (synth/generate 50)  200))
    (print-row "synthetic 200 rules"   (benchmark (synth/generate 200) 100))
    (print-row "synthetic 500 rules"   (benchmark (synth/generate 500) 50))))
