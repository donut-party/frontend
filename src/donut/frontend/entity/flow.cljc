(ns donut.frontend.entity.flow
  (:require
   [donut.compose :as dc]
   [donut.frontend.events :as dfe]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.sync.flow :as dsf]))

(defn get-entity-set-form-event
  [{:keys [route-name donut.form/key]}]
  [::dsf/get {:route-name route-name
              ::dfe/pre   [dsf/not-active]
              ::dfe/xf    [dsf/use-current-route-params]
              ::dfe/on    {:success (dc/into [[::dff/set-form-with-routed-entity {:donut.form/key key}]])}}])
