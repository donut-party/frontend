(ns donut.frontend.example.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]))

(defn system-config
  "This is a function instead of a static value so that it will pick up
  reloaded changes"
  []
  (cond-> (meta-merge stconfig/default-config
                      #_{::stsda/sync-dispatch-fn {:global-opts {:with-credentials true}}
                       ::stfr/frontend-router   {:use :reitit
                                                 :routes froutes/frontend-routes}
                       ::stfr/sync-router       {:use :reitit
                                                 :routes (ig/ref ::eroutes/routes)}
                       ::stnf/global-lifecycle  {:before-enter [[::stnuf/clear :route]
                                                                [::bch/register-flash-msg]
                                                                [:ga/send-pageview]]}
                       ::stjehf/handlers        {}
                       ::eroutes/routes         ""})))

(defn -main []
  (rf/dispatch-sync [::stcf/init-system (system-config)])
  (rf/dispatch [::bch/init])
  (rf/dispatch-sync [::stnf/dispatch-current])
  (rdom/render [app/app] (stcu/el-by-id "app")))

(defonce initial-load (delay (-main)))
@initial-load

(defn stop [_]
  (when-let [system (:sweet-tooth/system @rfdb/app-db)]
    (ig/halt! system)))
