(ns donut.frontend.reference.components.home
  (:require
   [donut.frontend.nav.components :as dnc]
   [donut.frontend.reference.ui :as ui]))

(defn component
  []
  [:div
   [ui/h1 "Home"]
   [:ul
    [:li [ui/route-link {:route-name :core.flow} "donut.frontend.core.flow examples"]]
    [:li [ui/route-link {:route-name :sync.flow} "donut.frontend.sync.flow examples"]]
    [:li [ui/route-link {:route-name :nav.flow} "donut.frontend.nav.flow examples"]]
    [:li [ui/route-link {:route-name :form.flow} "donut.frontend.form.flow examples"]]]])
