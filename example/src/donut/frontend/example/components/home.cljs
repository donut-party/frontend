(ns donut.frontend.example.components.home
  (:require
   [donut.frontend.nav.components :as dnc]))

(defn component
  []
  [:div
   [:h1 "Home"]
   [:ul
    [:li [dnc/route-link :core.flow "donut.frontend.core.flow examples"]]
    [:li [dnc/route-link :sync.flow "donut.frontend.sync.flow examples"]]]])
