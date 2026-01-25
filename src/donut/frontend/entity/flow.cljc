(ns donut.frontend.entity.flow
  (:require
   [donut.compose :as dc]
   [donut.frontend.events :as dfe]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.sync.flow :as dsf]))

(defn get-routed-entity-set-form-event
  [opts]
  (prn "get routed")
  [::dsf/get (merge {::dfe/pre   [dsf/not-active]
                     ::dfe/xf    [dsf/use-current-route-params]
                     ::dfe/on    {:success (dc/into [[::dff/set-form-with-routed-entity opts]])}}
                    opts)])
