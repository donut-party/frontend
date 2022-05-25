(ns donut.frontend.config
  (:require
   [re-frame.core :as rf]
   [donut.frontend.handlers :as dh]
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.routes :as dr]
   [donut.frontend.routes.reitit :as drr]
   [donut.frontend.sync.dispatch.ajax :as dsda]
   [donut.system :as ds]))


(def default-config
  {::ds/defs
   {:donut.frontend
    {:handlers             dh/AddInterceptorsComponent
     :sync                 {:router           (ds/local-ref [:sync-router])
                            :sync-dispatch-fn (ds/local-ref [:sync-dispatch-fn])}
     :sync-dispatch-fn     #::ds{:start dsda/system-sync-dispatch-fn}
     :frontend-router      #::ds{:start  dr/start-frontend-router
                                 :config drr/config-defaults}
     :sync-router          #::ds{:start  dr/start-sync-router
                                 :config drr/config-defaults}
     :nav-global-lifecycle dnf/default-global-lifecycle
     :nav-handler          #::ds{:start  dnf/init-handler
                                 :stop   dnf/halt-handler!
                                 :config {:dispatch-route-handler ::dnf/dispatch-route
                                          :check-can-unload?      true
                                          :router                 (ds/local-ref [:frontend-router])
                                          :global-lifecycle       (ds/local-ref [:nav-global-lifecycle])}}
     :re-frame             #::ds{:start (constantly true)
                                 :stop  (fn [_] (rf/clear-subscription-cache!))}}}})
