(ns donut.frontend.config
  (:require
   [donut.frontend.handlers :as dh]
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.routes :as dr]
   [donut.frontend.routes.reitit :as drr]
   [donut.frontend.sync.dispatch.ajax :as dsda]
   [donut.system :as ds]))


(def default-config
  {::ds/defs
   {:donut.frontend
    {:handlers            dh/RegisterHandlersComponent
     :sync                {:router           (ds/ref :sync-router)
                           :sync-dispatch-fn (ds/ref :sync-dispatch-fn)}
     :sync-dispatch-fn    dsda/SyncDispatchFn
     :frontend-router     {:start dr/start-frontend-router
                           :conf  drr/config-defaults}
     :sync-router         {:start dr/start-sync-router
                           :conf  drr/config-defaults}
     :nav-global-lifecyle {}
     :nav-handler         {:start dnf/init-handler
                           :conf  {:dispatch-route-handler ::stnf/dispatch-route
                                   :check-can-unload?      true
                                   :router                 (ds/ref :frontend-router)
                                   :global-lifecycle       (ds/ref :nav-global-lifecycle)}}}}})
