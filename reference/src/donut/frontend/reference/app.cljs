(ns donut.frontend.reference.app
  (:require
   [re-frame.core :as rf]
   [donut.frontend.nav.components :as dnc]
   [donut.frontend.nav.flow :as dnf]))

(defn app
  []
  (let [main @(rf/subscribe [::dnf/routed-component :main])]
    [:div
     (let [route-name @(rf/subscribe [::dnf/route-name])]
       (when (not= :home route-name)
         [:div
          [dnc/route-link :home "home"]
          " "
          [dnc/route-link :core.flow "core"]
          " "
          [dnc/route-link :nav.flow "nav"]
          " "
          [dnc/route-link :form.flow "form"]]))
     main]))
