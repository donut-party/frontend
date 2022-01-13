(ns donut.frontend.example.app
  (:require [re-frame.core :as rf]
            [donut.frontend.example.core.flow-component :as decfc]))

(defn app
  []
  [:div
   [:h1 "donut.frontend.core.flow examples"]
   [decfc/examples]])