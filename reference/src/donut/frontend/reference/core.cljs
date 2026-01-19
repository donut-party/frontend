(ns donut.frontend.reference.core
  (:require
   ["react-dom/client" :refer [createRoot]]
   [donut.frontend.config :as dconf]
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.reference.app :as app]
   [donut.frontend.reference.frontend-routes :as frontend-routes]
   [donut.frontend.sync.dispatch.echo :as dsde]
   [donut.system :as ds]
   [re-frame.core :as rf]
   [reagent.core :as r]))

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

(defmethod ds/named-system :base
  [_]
  dconf/default-config)

(defmethod ds/named-system :frontend-dev
  [_]
  (ds/system :base
    {:donut.frontend {:sync-dispatch-fn dsde/sync-dispatch-fn
                      :sync-router      {::ds/config {:routes fake-endpoint-routes}}
                      :frontend-router  {::ds/config {:routes frontend-routes/routes}}}}))

(defonce root (createRoot (dcu/el-by-id "app")))

(defn ^:dev/after-load start []
  (prn "starting")
  (rf/dispatch-sync [::dcf/start-system (ds/system :frontend-dev)])
  (rf/dispatch-sync [::dnf/dispatch-current])
  (.render root (r/as-element [app/app])))

(defn init
  []
  (start))

(defn ^:dev/before-load stop [_]
  (rf/dispatch-sync [::dcf/stop-system]))
