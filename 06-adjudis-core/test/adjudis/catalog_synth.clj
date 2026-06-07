(ns adjudis.catalog-synth
  "Procedural catalog generator. Produces N plausible rules across all
   categories so we can stress-test engine throughput without waiting on
   real CMS data ingestion (Phase 2.B).

   Distribution mirrors a real dental catalog at order-of-magnitude:
     ~60% frequency limits
     ~15% age-appropriate
     ~15% fee schedule
     ~5%  pre-auth
     ~3%  annual max
     ~2%  eligibility")

(def ^:private ada-codes
  ["D0120" "D0140" "D0150" "D0220" "D0274" "D0330"
   "D1110" "D1120" "D1206" "D1208" "D1330" "D1351" "D1352"
   "D2140" "D2150" "D2160" "D2330" "D2391" "D2740" "D2750" "D2790" "D2792"
   "D3220" "D3310" "D3330"
   "D4341" "D4910"
   "D5110" "D5120" "D5211" "D5212"
   "D6240" "D6750"
   "D7140" "D7210" "D7240"
   "D8080" "D8090"
   "D9110" "D9215" "D9230"])

(defn- frequency-rule [n]
  (let [code (nth ada-codes (mod n (count ada-codes)))]
    {:rule-id     (format "SYN-FREQ-%04d" n)
     :category    :frequency-limit
     :description (format "Synthetic frequency rule #%04d for %s" n code)
     :severity    :deny
     :reason-code (format "SYN-FREQ-%s" code)
     :params      {:procedure-codes #{code} :max-per-year (inc (mod n 4))}
     :citation    {:source (format "Synthetic catalog v%d" n)}}))

(defn- age-rule [n]
  (let [code (nth ada-codes (mod n (count ada-codes)))]
    {:rule-id     (format "SYN-AGE-%04d" n)
     :category    :age-appropriate
     :description (format "Synthetic age rule #%04d for %s" n code)
     :severity    :deny
     :reason-code (format "SYN-AGE-%s" code)
     :params      (if (even? n)
                    {:procedure-codes #{code} :max-age (+ 12 (mod n 8))}
                    {:procedure-codes #{code} :min-age (+ 14 (mod n 6))})
     :citation    {:source "Synthetic age catalog"}}))

(defn- fee-schedule-rule [n]
  (let [batch (take 5 (drop (mod n (count ada-codes)) (cycle ada-codes)))]
    {:rule-id     (format "SYN-FEE-%04d" n)
     :category    :fee-schedule
     :description (format "Synthetic fee schedule chunk #%04d" n)
     :severity    :adjust
     :reason-code "FEE-ADJ"
     :params      {:network :in-network
                   :schedule (into {} (map-indexed (fn [i c] [c (+ 40.0 (* 25.0 i))]) batch))}
     :citation    {:source "Synthetic in-network fee schedule"}}))

(defn- pre-auth-rule [n]
  (let [code (nth ada-codes (mod (* n 3) (count ada-codes)))]
    {:rule-id     (format "SYN-PA-%04d" n)
     :category    :pre-auth-required
     :description (format "Synthetic pre-auth rule #%04d for %s" n code)
     :severity    :pending
     :reason-code "PREAUTH-REQUIRED"
     :params      {:procedure-codes #{code} :threshold-amount (* 100.0 (inc (mod n 9)))}
     :citation    {:source "Synthetic pre-auth catalog"}}))

(defn- annual-max-rule [n]
  {:rule-id     (format "SYN-MAX-%04d" n)
   :category    :annual-maximum
   :description (format "Synthetic annual-max rule #%04d" n)
   :severity    :pending
   :reason-code "ANN-MAX-EXCEEDED"
   :params      {:max-amount (+ 1000.0 (* 100.0 (mod n 15)))}
   :citation    {:source "Synthetic plan max"}})

(defn- eligibility-rule [n]
  {:rule-id     (format "SYN-ELIG-%04d" n)
   :category    :eligibility
   :description "Synthetic eligibility rule"
   :severity    :deny
   :reason-code "ELIG-INACTIVE"
   :params      {}
   :citation    {:source "270/271"}})

(defn generate
  "Produce a vector of n catalog rules with a realistic category distribution."
  [n]
  (let [n-freq (int (* n 0.60))
        n-age  (int (* n 0.15))
        n-fee  (int (* n 0.15))
        n-pa   (int (* n 0.05))
        n-max  (int (* n 0.03))
        n-elig (- n n-freq n-age n-fee n-pa n-max)]
    (into []
          (concat
           (map frequency-rule    (range n-freq))
           (map age-rule          (range n-age))
           (map fee-schedule-rule (range n-fee))
           (map pre-auth-rule     (range n-pa))
           (map annual-max-rule   (range n-max))
           (map eligibility-rule  (range n-elig))))))
