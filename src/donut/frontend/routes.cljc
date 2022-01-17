(ns donut.frontend.routes
  (:require [donut.frontend.routes.protocol :as drp]
            [clojure.spec.alpha :as s]))

(comment
  (s/def ::route-name keyword?)
  (s/def ::params map?)
  (s/def ::lifecycle map?)

  (s/def ::route (s/keys :req-un [::route-name
                                  ::params]
                         :opt-un [::lifecycle]))

  (s/def ::router (s/keys :req-un [::routes])))

(def frontend-router nil)
(def sync-router nil)

(defn path
  [name & [route-params query-params]]
  (drp/path frontend-router name route-params query-params))

(defn route
  [path-or-name & [route-params query-params]]
  (drp/route frontend-router path-or-name route-params query-params))

(defn api-path
  [name & [route-params query-params]]
  (drp/path sync-router name route-params query-params))

(defn req-id
  [name & [route-params]]
  (drp/req-id sync-router name route-params))

(defn start-frontend-router
  [conf _ _]
  (set! frontend-router (drp/router conf)))

(defn start-sync-router
  [conf _ _]
  (set! sync-router (drp/router conf)))
