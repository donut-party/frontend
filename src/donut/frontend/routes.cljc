(ns donut.frontend.routes
  (:require [integrant.core :as ig]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [clojure.spec.alpha :as s]))

(s/def ::route-name keyword?)
(s/def ::params map?)
(s/def ::lifecycle map?)

(s/def ::route (s/keys :req-un [::route-name
                                ::params]
                       :opt-un [::lifecycle]))

(s/def ::router (s/keys :req-un [::routes]))

(def frontend-router (atom nil))
(def sync-router (atom nil))

(defn path
  [name & [route-params query-params]]
  (strp/path @frontend-router name route-params query-params))

(defn route
  [path-or-name & [route-params query-params]]
  (strp/route @frontend-router path-or-name route-params query-params))

(defn api-path
  [name & [route-params query-params]]
  (strp/path @sync-router name route-params query-params))

(defn req-id
  [name & [route-params]]
  (strp/req-id @sync-router name route-params))

(defmethod ig/init-key ::frontend-router [_ config]
  (reset! frontend-router (strp/router config)))

(defmethod ig/init-key ::sync-router [_ config]
  (reset! sync-router (strp/router config)))
