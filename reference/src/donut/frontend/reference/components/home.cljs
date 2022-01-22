(ns donut.frontend.reference.components.home
  (:require
   [donut.frontend.nav.components :as dnc]))

(defn component
  []
  [:div
   [:h1 "Home"]
   [:ul
    [:li [dnc/route-link {:route-link :core.flow} "donut.frontend.core.flow examples"]]
    [:li [dnc/route-link {:route-link :sync.flow} "donut.frontend.sync.flow examples"]]
    [:li [dnc/route-link {:route-link :nav.flow} "donut.frontend.nav.flow examples"]]
    [:li [dnc/route-link {:route-link :form.flow} "donut.frontend.form.flow examples"]]]])
