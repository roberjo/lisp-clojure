(ns edi.transform.core
  "Transduce a CL-emitted X12 transaction plist into a normalized claim map.

   Why transducers? The pipeline is segment-oriented: filter to the segments
   of interest, then collapse them into a single accumulator. xform composition
   means the same logic runs over an in-memory vector OR a core.async channel
   when a streaming version is added — without changes to the transform itself."
  (:require [edi.transform.schema :as schema]))

;; ---------- plist <-> map ----------

(defn plist->map
  "CL plists arrive as flat vectors of alternating :key value pairs in EDN.
   Convert to a Clojure map."
  [pl]
  (->> pl
       (partition 2)
       (map (fn [[k v]] [k v]))
       (into {})))

(defn segment-plist->map [seg-pl]
  (let [m (plist->map seg-pl)]
    {:id       (:id m)
     :elements (vec (:elements m))}))

(defn transaction-plist->map [tx-pl]
  (let [m (plist->map tx-pl)]
    {:type           (:type m)
     :control-number (:control-number m)
     :segments       (vec (map segment-plist->map (:segments m)))}))

;; ---------- Transducers ----------

(defn segments-of [id]
  (filter #(= id (:id %))))

(def all-service-lines
  "xform: segments -> service-line maps. SV3 lines are dental; numbering is
   by appearance order."
  (comp
   (segments-of "SV3")
   (map-indexed
    (fn [i seg]
      (let [[proc-with-qualifier charge _ _ units] (:elements seg)
            [_qual proc-code]                      (some-> proc-with-qualifier (clojure.string/split #":" 2))]
        (cond-> {:line-number    (inc i)
                 :procedure-code (or proc-code proc-with-qualifier)
                 :charge         (Double/parseDouble charge)}
          units (assoc :units (Integer/parseInt units))))))))

;; ---------- Reducers + helpers ----------

(defn- find-segment [tx id]
  (some #(when (= id (:id %)) %) (:segments tx)))

(defn- find-nm1 [tx entity-code]
  (some (fn [seg]
          (when (and (= "NM1" (:id seg))
                     (= entity-code (first (:elements seg))))
            seg))
        (:segments tx)))

(defn- ->transaction-type [k]
  (case k
    :dental-claim-transaction :dental
    :claim-transaction        :professional
    :remittance-transaction   :remittance
    :transaction              :unknown
    :unknown))

(defn- ->subscriber [nm1-il]
  (when nm1-il
    (let [[_entity _qual last-name first-name _middle _ _ id-qual id-code]
          (:elements nm1-il)]
      (cond-> {:last (or last-name "")}
        (seq first-name) (assoc :first first-name)
        (seq id-code)    (assoc :member-id id-code)
        (= "MI" id-qual) identity))))

;; ---------- Public API ----------

(defn transaction->claim
  "Top-level transform. Input is the EDN plist as produced by project 02's
   bin/emit-plist.lisp; output conforms to schema/NormalizedClaim."
  [tx-pl]
  (let [tx        (transaction-plist->map tx-pl)
        clm       (find-segment tx "CLM")
        bill-prov (find-nm1 tx "85")
        sub       (find-nm1 tx "IL")
        sv-lines  (into [] all-service-lines (:segments tx))
        date-seg  (find-segment tx "DTP")
        sv-lines  (if (and (seq sv-lines) date-seg)
                    (mapv #(assoc % :service-date (nth (:elements date-seg) 2 nil))
                          sv-lines)
                    sv-lines)]
    {:transaction-type (->transaction-type (:type tx))
     :control-number   (:control-number tx)
     :claim-id         (first (:elements clm))
     :total-charge     (Double/parseDouble (second (:elements clm)))
     :billing-provider (some-> bill-prov :elements (nth 2 nil))
     :subscriber       (->subscriber sub)
     :service-lines    sv-lines}))
