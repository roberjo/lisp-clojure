(ns adjudis.tenants
  "Tenant configuration and rule overlays.

   A tenant is identified by an opaque id (e.g. 'acme-dental') and carries:
     - a name for display
     - an API key for authentication
     - an optional rule overlay that customizes the shipped catalog

   Overlay grammar (all keys optional):
     {:add      [rule-map ...]        ; rules unique to this tenant
      :override [rule-map ...]        ; rules with same :rule-id as shipped, replace
      :remove   #{\"rule-id\" ...}}   ; shipped rule-ids suppressed for this tenant

   The composition function applies them in this order:
     effective = (base - remove-ids - override-ids) + override + add

   This is the right shape because real customers ask for:
   - 'we never pay for D2740 even if scheduled' → :add a deny
   - 'our annual max is $2500 not $1500'        → :override the shipped rule
   - 'we don't enforce pre-auth on minors'      → :remove a shipped rule

   Tenant data isolation: the engine takes the effective catalog as an input;
   handlers per-tenant compute it once per request and pass it in. No shared
   mutable state between tenants in the engine path."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))

;; ──────────────────────────────────────────────────────────────────────────
;; Loading
;; ──────────────────────────────────────────────────────────────────────────

(defn load-tenants
  "Read tenants from an EDN file. Shape:
     {:tenants {<tenant-id> {:name        \"...\"
                              :api-key     \"...\"
                              :overlay-file \"path/to/overlay.edn\"  ;; optional
                              :overlay     {:add [] :override [] :remove #{}}  ;; or inline
                              }}}"
  [path]
  (let [raw (with-open [r (java.io.PushbackReader. (io/reader path))]
              (edn/read r))]
    (:tenants raw)))

(defn load-overlay
  "Load an overlay either inline from the tenant map's :overlay key, or from
   the file referenced by :overlay-file (path is resolved relative to the
   tenants config). Returns an overlay map (possibly empty)."
  [tenant base-dir]
  (cond
    (:overlay tenant) (:overlay tenant)

    (:overlay-file tenant)
    (let [path (io/file base-dir (:overlay-file tenant))]
      (with-open [r (java.io.PushbackReader. (io/reader path))]
        (edn/read r)))

    :else {}))

;; ──────────────────────────────────────────────────────────────────────────
;; Auth lookup
;; ──────────────────────────────────────────────────────────────────────────

(defn lookup-by-api-key
  "Linear scan — fine for the tens of tenants you'd reasonably keep in a
   single config. Phase 3.B would replace with a database-backed lookup."
  [tenants api-key]
  (when (and api-key tenants)
    (some (fn [[tid t]] (when (= api-key (:api-key t))
                          (assoc t :tenant-id tid)))
          tenants)))

;; ──────────────────────────────────────────────────────────────────────────
;; Overlay composition
;; ──────────────────────────────────────────────────────────────────────────

(defn- by-id [rules] (into {} (map (juxt :rule-id identity) rules)))

(defn apply-overlay
  "Compose base catalog with a tenant overlay.

     effective = (base - remove-ids - override-ids) + override + add

   Returns a flat vector of rule maps."
  [base overlay]
  (let [adds      (vec (:add      overlay))
        overrides (vec (:override overlay))
        removes   (or (:remove overlay) #{})
        override-ids (set (map :rule-id overrides))
        suppressed   (set/union removes override-ids)
        kept-base    (filterv #(not (suppressed (:rule-id %))) base)]
    (into kept-base (concat overrides adds))))

(defn effective-catalog
  "Top-level: given the shipped base catalog and a tenant map (with :overlay
   either inline or loadable from :overlay-file at base-dir), return the
   tenant's effective catalog. Pure; called per request."
  [base tenant base-dir]
  (apply-overlay base (load-overlay tenant base-dir)))
