(ns clj-workcalendar.api
  (:require [clj-workcalendar.calendar-data :as data]))

(defn- date2vec [date]
  #?(:clj    (vector (.getYear date)
                     (.getMonthValue date)
                     (.getDayOfMonth date))
     :cljs   (vector (.getFullYear date)
                     (inc (.getMonth date))
                     (.getDate date))))

(defn- add-days [date count]
  #?(:clj  (. date plusDays count)
     :cljs (js/Date. (+ (.getTime date)
                        (* 24 60 60 1000 count)))))

(defn- skip-time [date]
  #?(:clj  (java.time.LocalDateTime/of (.getYear date)
                                       (.getMonth date)
                                       (.getDayOfMonth date)
                                       0
                                       0
                                       0)
     :cljs   (js/Date. (.getFullYear date)
                       (.getMonth date)
                       (.getDate date))))

(defn- day-of-week [date]
  #?(:clj    (.getDayOfWeek date)
     :cljs   (.getDay date)))

(defn- millis [date]
  #?(:clj (-> date
              (.atZone (java.time.ZoneId/systemDefault))
              (.toInstant)
              (.toEpochMilli))
     :cljs (.getTime date)))

(def work-calendar-map
  ;; create map from [y m d] -> day type (:holiday :workday)
  (apply hash-map
         (mapcat (fn [[year month day type]]
                   (vector [year month day] type))
                 data/work-calendar)))

(defn ^:export is-weekend [date]
  (let [day (day-of-week date)]
    #?(:clj (or (= day java.time.DayOfWeek/SATURDAY)
                (= day java.time.DayOfWeek/SUNDAY))
       :cljs (or (= day 0)
                 (= day 6)))))

(defn ^:export is-special-working-day [date]
  (= :workday (get work-calendar-map (date2vec date) :not-found)))

(defn ^:export is-holiday [date]
  (= :holiday (get work-calendar-map (date2vec date) :not-found)))

(defn ^:export is-non-working-day [date]
  (or (and (is-weekend date)
           (not (is-special-working-day date)))
      (and (not (is-weekend date))
           (is-holiday date))))

(defn ^:export is-workday [date]
  (not (is-non-working-day date)))

(defn ^:export get-nearest-workday [date move-backwards]
  (let [direction (if move-backwards -1 1)]
    (first
     (filter is-workday
             (iterate #(add-days % direction) date)))))

(defn ^:export move-to-workday [date]
  (get-nearest-workday date false))

(defn ^:export move-to-workday-backwards [date]
  (get-nearest-workday date true))

(defn ^:export add-work-days [date count]
  (if (or (zero? count)
          (nil? count))
    date
    (let [n (Math/abs count)
          direction (if (> count 0) 1 -1)]
      (first
       (drop n
             (filter is-workday
                     (iterate #(add-days % direction) date)))))))

(defn ^:export work-day-count [start-date end-date]
  (if (> (millis start-date)
         (millis end-date))
    #?(:clj (throw (new RuntimeException "invalid period"))
       :cljs (throw (js/Error "invalid period")))
    (let [from (skip-time start-date)
          to (millis (skip-time end-date))]
      (count
       (filter is-workday
               (take-while #(<= (millis %) to)
                           (iterate (fn [d] (add-days d 1)) from)))))))
