(ns donut.frontend.reference.frontend-routes
  (:require
   [donut.frontend.events :as dfe]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.reference.components.home :as home]
   [donut.frontend.reference.core.examples :as dece]
   [donut.frontend.reference.form.examples :as defe]
   [donut.frontend.reference.nav.examples :as dene]
   [donut.frontend.reference.sync.examples :as dese]
   [re-frame.core :as rf]
   [reitit.coercion.malli :as rm]))

(rf/reg-event-fx ::param-change-example
  (fn [_ _]
    (js/console.log "param change example!")))

(defn nav-flow-can-exit?
  [db _old-route _new-route]
  (not (get db ::dene/prevent-change)))

(def routes
  [["/"
    {:name       :home
     :components {:main [home/component]}
     :title      "Donut Examples"}]

   ["/core.flow"
    {:name       :core.flow
     :components {:main [dece/examples]}
     :title      "Donut Examples"}]

   ["/sync.flow"
    {:name       :sync.flow
     :components {:main [dese/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow"
    {:name       :nav.flow
     :lifecycle  {:can-exit? nav-flow-can-exit?}
     :components {:main [dene/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/1"
    {:name       :nav.flow-1
     ::dfe/on    {:enter [[::dene/inc-flow-1-lifecycle-fire-count]]}
     ::dfe/can?  {:exit nav-flow-can-exit?}
     :components {:main [dene/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/2"
    {:name       :nav.flow-2
     ::dfe/on    {:enter [(fn [_]
                            [[::dene/set-flow-2-val (str (random-uuid))]])]}
     ::dfe/can?  {:exit nav-flow-can-exit?}
     :components {:main [dene/examples]}
     :title      "Donut Examples"}]

   ["/nav.flow/routed-entity/{id}"
    {:name       :nav.flow.routed-entity
     :lifecycle  {:can-exit? nav-flow-can-exit?}
     :components {:main [dene/examples]}
     :title      "Donut Examples"
     :coercion   rm/coercion
     :parameters {:path [:map [:id int?]]}}]

   ["/form.flow"
    {:name       :form.flow
     :lifecycle  {:exit [[::dff/clear [:post :users]]]}
     :components {:main [defe/examples]}
     :title      "Donut Examples"}]])
