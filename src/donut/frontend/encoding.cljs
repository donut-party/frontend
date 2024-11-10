(ns donut.frontend.encoding
  (:require
   [cognitect.transit :as transit]))

(def write-handlers
  {js/Date (transit/write-handler (constantly "time/instant")
                                  (fn [d] (.toISOString d)))})

(def read-handlers
  {"time/instant" (transit/read-handler (fn [s] (js/Date. s)))})
