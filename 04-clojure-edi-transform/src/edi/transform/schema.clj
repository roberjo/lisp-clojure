(ns edi.transform.schema
  "Malli schemas for the input plist-as-EDN and the normalized JSON output.
   Chose Malli over clojure.spec for the readable, data-first schema syntax
   and the much better error messages out of the box."
  (:require [malli.core :as m]))

;; ---------- Input (what project 02 emits as plist-EDN) ----------

(def Segment
  [:map
   [:id       :string]
   [:elements [:vector [:maybe :string]]]])

(def Transaction
  [:map
   [:type           :keyword]
   [:control-number :string]
   [:segments       [:vector Segment]]])

;; ---------- Output (normalized claim) ----------

(def ServiceLine
  [:map
   [:line-number       :int]
   [:procedure-code    :string]
   [:charge            :double]
   [:service-date      {:optional true} :string]
   [:oral-cavity       {:optional true} :string]
   [:units             {:optional true} :int]])

(def NormalizedClaim
  [:map
   [:transaction-type   [:enum :dental :professional :institutional :remittance :unknown]]
   [:control-number     :string]
   [:claim-id           :string]
   [:total-charge       :double]
   [:billing-provider   [:maybe :string]]
   [:subscriber         [:maybe [:map
                                 [:last  :string]
                                 [:first {:optional true} :string]
                                 [:member-id {:optional true} :string]]]]
   [:service-lines      [:vector ServiceLine]]])

(defn validate-transaction [tx]
  (m/explain Transaction tx))

(defn validate-claim [claim]
  (m/explain NormalizedClaim claim))
