(ns cljs-workcalendar.spider.consultantru
  (:require [cljs-workcalendar.workdayitem])
  (:import  [cljs_workcalendar.workdayitem WorkDayItem])
  (:require [cljs-workcalendar.spider.workcalendarsource :as source])
  (:require [net.cgrand.enlive-html :as html])
  (:require [clj-http.client :as http]))

(defrecord ConsultantRuWorkCalendarSource [])

(defn- day-placeholder? [html-element]
  (= (:class (:attrs html-element)) "disabled"))

(defn- weekend? [html-element]
  (= (:class (:attrs html-element)) "weekend"))

(defn- day [html-element]
  (Integer/parseInt (first (:content html-element))))

(defn- extract-non-working-days [calendar-element year month]
  (filter #(not (nil? %))
          (map (fn [day-element day-of-week]
                 (cond
                   (day-placeholder? day-element)
                   nil

                   (and (>= day-of-week 1)
                        (<= day-of-week 5)
                        (weekend? day-element))
                   (WorkDayItem. year month (day day-element) :holiday)

                   (and (>= day-of-week 6)
                        (<= day-of-week 7)
                        (not (weekend? day-element)))
                   (WorkDayItem. year month (day day-element) :workday)

                   true
                   nil))
               (drop 7 (html/select calendar-element [:.days :span]))
               (apply concat (repeatedly #(range 1 8))))))

(defn- get-work-calendar-for-year [year]
  (let [rawPage (:body (http/get "http://www.consultant.ru/law/ref/calendar/proizvodstvennye/"))
        page (html/html-resource (java.io.StringReader. rawPage))
        month-calendars (html/select page [:.month-block])]
    (apply concat (map (fn [calendar month-index]
                         (extract-non-working-days calendar year month-index))
                       month-calendars
                       (iterate inc 1)))))

(extend-protocol source/WorkCalendarSource
  ConsultantRuWorkCalendarSource
  (get-work-calendar [this]
    (get-work-calendar-for-year 2015)))
