(ns donut.frontend.entity.flow
  (:require
   [donut.frontend.events :as dfe]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.sync.flow :as dsf]))

(defn get-routed-entity-set-form-event
  [opts]
  [::dsf/get (merge {::dfe/pre   [dsf/not-active]
                     ::dfe/xf    [dsf/use-current-route-params]
                     ::dfe/on    {:success [[::dsf/default-sync-success]
                                            [::dff/set-form-with-routed-entity opts]]}}
                    opts)])
