(ns donut.frontend.example.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]
            [donut.frontend.example.app :as app]
            [donut.frontend.config :as dconf]
            [donut.frontend.core.flow :as dcf]
            [donut.frontend.core.utils :as dcu]
            [donut.system :as ds]))

(defn system-config
  "This is a function instead of a static value so that it will pick up
  reloaded changes"
  []
  ;; (cond-> (meta-merge stconfig/default-config
  ;;                     {::stsda/sync-dispatch-fn {:global-opts {:with-credentials true}}
  ;;                      ::stfr/frontend-router   {:use :reitit
  ;;                                                :routes froutes/frontend-routes}
  ;;                      ::stfr/sync-router       {:use :reitit
  ;;                                                :routes (ig/ref ::eroutes/routes)}
  ;;                      ::stnf/global-lifecycle  {:before-enter [[::stnuf/clear :route]
  ;;                                                               [::bch/register-flash-msg]
  ;;                                                               [:ga/send-pageview]]}
  ;;                      ::stjehf/handlers        {}
  ;;                      ::eroutes/routes         ""}))
  {::ds/defs {}}
  dconf/default-config
  )

(defn -main []
  ;; (rf/dispatch-sync [::stcf/init-system (system-config)])
  ;; (rf/dispatch [::bch/init])
  ;; (rf/dispatch-sync [::stnf/dispatch-current])
  (rf/dispatch-sync [::dcf/init-system (system-config)])
  (rdom/render [app/app] (dcu/el-by-id "app")))

(defonce initial-load (delay (-main)))
@initial-load

(defn stop [_]
  (rf/clear-subscription-cache!))
