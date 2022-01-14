(ns donut.frontend.example.app
  (:require
   [donut.frontend.example.core.flow-component :as decfc]
   [donut.frontend.example.sync.flow-component :as desfc]))

(defn app
  []
  [:div
   [decfc/examples]
   [desfc/examples]])
