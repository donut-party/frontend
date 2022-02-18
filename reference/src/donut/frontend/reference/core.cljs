(ns donut.frontend.reference.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [donut.frontend.config :as dconf]
            [donut.frontend.core.flow :as dcf]
            [donut.frontend.core.utils :as dcu]
            [donut.frontend.reference.app :as app]
            [donut.frontend.reference.frontend-routes :as frontend-routes]
            [donut.frontend.nav.flow :as dnf]
            [donut.frontend.sync.dispatch.echo :as dsde]
            [donut.system :as ds]
            [meta-merge.core :as meta-merge]))

(def fake-endpoint-routes
  "We're not making requests to real endpoints,"
  [["/user"      {:name     :users
                  :ent-type :user
                  :id-key   :id}]
   ["/user/{id}" {:name     :user
                  :ent-type :user
                  :id-key   :id}]
   ["/post" {:name     :posts
             :ent-type :post
             :id-key   :id}]
   ["/post/{id}" {:name     :post
                  :ent-type :post
                  :id-key   :id}]])

(defn system-config
  "This is a function instead of a static value so that it will pick up
  reloaded changes"
  []
  (meta-merge/meta-merge
   dconf/default-config
   {::ds/defs
    {:donut.frontend
     {:sync-dispatch-fn dsde/sync-dispatch-fn
      :sync-router      {:conf {:routes fake-endpoint-routes}}
      :frontend-router  {:conf {:routes frontend-routes/routes}}}}}))

(defn ^:dev/after-load start []
  ;; (rf/dispatch-sync [::stcf/init-system (system-config)])
  ;; (rf/dispatch [::bch/init])
  ;; (rf/dispatch-sync [::stnf/dispatch-current])
  (rf/dispatch-sync [::dcf/start-system (system-config)])
  (rf/dispatch-sync [::dnf/dispatch-current])
  (rdom/render [app/app] (dcu/el-by-id "app")))

(defn init []
  (start))

(defn ^:dev/before-load stop [_]
  (rf/dispatch-sync [::dcf/stop-system]))
