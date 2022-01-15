(ns donut.frontend.example.frontend-routes
  (:require
   [re-frame.core :as rf]
   [donut.frontend.example.components.home :as home]
   [donut.frontend.example.core.flow-component :as decfc]
   [donut.frontend.example.nav.flow-component :as denfc]
   [donut.frontend.example.sync.flow-component :as desfc]))

(rf/reg-event-fx ::param-change-example
  (fn [_ _]
    (js/console.log "param change example!")))

(def routes
  [["/"
    {:name       :home
     :components {:main [home/component]}
     :title      "Donut Examples"}]

   ["/core.flow"
    {:name       :core.flow
     :components {:main [decfc/examples]}
     :title      "Donut Examples"}]

   ["/sync.flow"
    {:name       :sync.flow
     :components {:main [desfc/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow"
    {:name       :nav.flow
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}])}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/1"
    {:name       :nav.flow-1
     :lifecycle  {:param-change [::denfc/inc-flow-1-lifecycle-fire-count]}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/2"
    {:name       :nav.flow-2
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}]
                                  [::denfc/set-flow-2-val (str (random-uuid))])}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"}]
   ])
