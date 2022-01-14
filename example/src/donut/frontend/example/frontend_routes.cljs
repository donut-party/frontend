(ns donut.frontend.example.frontend-routes
  (:require
   [donut.frontend.example.components.home :as home]
   [donut.frontend.example.core.flow-component :as decfc]
   [donut.frontend.example.sync.flow-component :as desfc]))

(def routes
  [["/"
    {:name       :home
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}])}
     :components {:main [home/component]}
     :title      "Donut Examples"}]

   ["/core.flow"
    {:name       :core.flow
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}])}
     :components {:main [decfc/examples]}
     :title      "Donut Examples"}]

   ["/sync.flow"
    {:name       :sync.flow
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}])}
     :components {:main [desfc/examples]}
     :title      "Donut Examples"}]])
