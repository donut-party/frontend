(ns donut.frontend.date-time)

(def relative-time-format
  (js/Intl.RelativeTimeFormat.))

(defn time-ago
  ([datetime]
   (time-ago datetime relative-time-format))
  ([datetime rtf]
   (let [diff    (/ (- (.getTime (js/Date.))
                       (.getTime datetime))
                    1000)
         minutes (Math/floor (/ diff 60))
         hours   (Math/floor (/ minutes 60))
         days    (Math/floor (/ hours 24))
         months  (Math/floor (/ days 30))
         years   (Math/floor (/ months 12))]
     (cond
       (> years 0)   (.format rtf (* -1 years) "years")
       (> months 0)  (.format rtf (* -1 months) "months")
       (> days 0)    (.format rtf (* -1 days) "days")
       (> hours 0)   (.format rtf (* -1 hours) "hours")
       (> minutes 0) (.format rtf (* -1 minutes) "minutes")
       :else         (.format rtf (* -1 diff) "seconds")))))
