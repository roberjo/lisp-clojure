(ns adjudis.dates
  "Small date helpers. The MVP uses ISO date strings; Phase 2 will switch to
   java.time.LocalDate throughout."
  (:import [java.time LocalDate Period]
           [java.time.format DateTimeFormatter]))

(def ^:private iso DateTimeFormatter/ISO_LOCAL_DATE)

(defn parse
  "Lenient parse: accept ISO yyyy-MM-dd, or compact CCYYMMDD (as X12 D8)."
  [s]
  (when s
    (let [clean (clojure.string/replace s #"[^0-9-]" "")]
      (cond
        (re-matches #"\d{4}-\d{2}-\d{2}" clean) (LocalDate/parse clean iso)
        (re-matches #"\d{8}" clean)
        (LocalDate/parse (str (subs clean 0 4) "-" (subs clean 4 6) "-" (subs clean 6 8)) iso)
        :else nil))))

(defn ->iso [^LocalDate d] (when d (.format d iso)))

(defn years-between [from to]
  (when (and from to)
    (.getYears (Period/between from to))))

(defn age-on
  "Age of a member with DOB d on date dos. Both args are strings or LocalDate."
  [dob-input dos-input]
  (let [dob (if (string? dob-input) (parse dob-input) dob-input)
        dos (if (string? dos-input) (parse dos-input) dos-input)]
    (years-between dob dos)))

(defn within?
  "Is dos within [start, end]? Either bound may be nil (open-ended)."
  [dos-input start-input end-input]
  (let [dos   (if (string? dos-input)   (parse dos-input)   dos-input)
        start (if (string? start-input) (parse start-input) start-input)
        end   (if (string? end-input)   (parse end-input)   end-input)]
    (and dos
         (or (nil? start) (not (.isBefore dos start)))
         (or (nil? end)   (not (.isAfter  dos end))))))

(defn same-benefit-year?
  "MVP: calendar year. Phase 2 will support plan-year-anchored windows."
  [dos1-input dos2-input]
  (let [d1 (if (string? dos1-input) (parse dos1-input) dos1-input)
        d2 (if (string? dos2-input) (parse dos2-input) dos2-input)]
    (and d1 d2 (= (.getYear d1) (.getYear d2)))))
