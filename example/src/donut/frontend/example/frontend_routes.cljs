(ns donut.frontend.example.frontend-routes
  (:require
   [re-frame.core :as rf]
   [donut.frontend.example.components.home :as home]
   [donut.frontend.example.core.flow-component :as decfc]
   [donut.frontend.example.form.flow-component :as deffc]
   [donut.frontend.example.nav.flow-component :as denfc]
   [donut.frontend.example.sync.flow-component :as desfc]
   [reitit.coercion.malli :as rm]))

(rf/reg-event-fx ::param-change-example
  (fn [_ _]
    (js/console.log "param change example!")))

(defn nav-flow-can-exit?
  [db _old-route _new-route]
  (not (get db ::denfc/prevent-change)))

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
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}])
                  :can-exit?    nav-flow-can-exit?}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/1"
    {:name       :nav.flow-1
     :lifecycle  {:param-change [::denfc/inc-flow-1-lifecycle-fire-count]
                  :can-exit?    nav-flow-can-exit?}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/2"
    {:name       :nav.flow-2
     :lifecycle  {:param-change (fn [_ _ {:keys [params]}]
                                  [::denfc/set-flow-2-val (str (random-uuid))])
                  :can-exit?    nav-flow-can-exit?}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/routed-entity/{id}"
    {:name       :nav.routed-entity
     :lifecycle  {:can-exit? nav-flow-can-exit?}
     :components {:main [denfc/examples]}
     :title      "Donut Examples"
     :coercion   rm/coercion
     :parameters {:path [:map [:id int?]]}}]

   ["/form.flow"
    {:name       :form.flow
     :components {:main [deffc/examples]}
     :title      "Donut Examples"}]])
