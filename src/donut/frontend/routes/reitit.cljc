(ns donut.frontend.routes.reitit
  (:require
   [clojure.set :as set]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.routes.protocol :as drp]
   [re-frame.loggers :as rfl]
   [reitit.coercion :as coercion]
   [reitit.coercion.malli :as rm]
   [reitit.core :as rc]
   [reitit.frontend :as reif]
   [clojure.walk :as walk]))

(defn no-path-data
  [route-name match route-params]
  (let [required          (set (get match :required))
        provided-required (select-keys route-params required)
        missing-required  (set/difference required (set (keys provided-required)))
        data              {:route-name   route-name
                           :route-params provided-required
                           :required     required
                           :match        (-> match
                                             (dissoc :data :required)
                                             (update :path-params select-keys required))}
        message           (cond
                            (not match)
                            (str "there is no route named `" route-name "`")

                            (seq missing-required)
                            (str "missing required params for route `" route-name "`: "
                                 missing-required))]
    {:message message
     :data    data}))

(defn on-no-path-warn
  [route-name match route-params]
  (let [{:keys [message data]} (no-path-data route-name match route-params)]
    (rfl/console :warn message data)))

(defn on-no-path-throw
  [route-name match route-params]
  (let [{:keys [message data]} (no-path-data route-name match route-params)]
    (throw (ex-info message data))))

(defn on-no-route-warn
  [path-or-name route-params query-params]
  (rfl/console :warn
               "reitit could not match route"
               {:route-args [path-or-name route-params query-params]}))

(defn on-no-route-throw
  [path-or-name route-params query-params]
  (throw
   (ex-info "reitit could not match route"
            {:route-args [path-or-name route-params query-params]})))

(def config-defaults
  {:use         :reitit
   :on-no-path  on-no-path-warn
   :on-no-route on-no-route-warn
   :entities    [:donut.system/local-ref [:entities]]})

(defrecord ReititRouter [routes router on-no-path on-no-route]
  drp/Router
  (drp/path
    [_this {:keys [route-name route-params query-params params] :as _req}]
    (let [{{:keys [prefix]} :data :as match} (rc/match-by-name router route-name (merge params route-params))]
      (if (and match (not (:required match)))
        (cond-> match
          true                     (rc/match->path)
          (not-empty query-params) (str "?" (dcu/params-to-str query-params))
          prefix                   (as-> p (str prefix  p)))
        (when on-no-path
          (on-no-path route-name match route-params)
          nil))))

  (drp/req-id
    [_this {:keys [route-name] :as req}]
    (when (and (some? req) (not (map? req)))
      (rfl/console :error "req-id req should be a map" {:req req}))
    (let [params (merge (:params req)
                        (:route-params req))]
      (select-keys params (:required (rc/match-by-name router route-name)))))

  (drp/route
    [this path-or-name]
    (drp/route this path-or-name {} {}))
  (drp/route
    [this path-or-name route-params]
    (drp/route this path-or-name route-params {}))
  (drp/route
    [_this path-or-name route-params query-params]
    (if-let [{:keys [data query-params] :as m} (if (keyword? path-or-name)
                                                 (rc/match-by-name router path-or-name route-params)
                                                 (reif/match-by-path router path-or-name))]
      (-> data
          (merge (dissoc m :data))
          (set/rename-keys {:name       :route-name
                            :parameters :params})
          (update :params (fn [{:keys [path query] :as _params}]
                            (merge path query query-params))))
      (when on-no-route
        (on-no-route path-or-name route-params query-params)
        nil))))

(defn merge-entity
  [entities [_path {:keys [ent-type] :as route-opts} :as route]]
  (if ent-type
    (let [entity-opts (ent-type entities)
          route-opts (merge (assoc (:route entity-opts) :id-key (:id-key entity-opts))
                            route-opts)]
      (assoc route 1 route-opts))
    route))

(defn merge-entities
  [entities routes]
  (walk/postwalk (fn [x]
                   (if (and (vector? x)
                            (map? (second x)))
                     (merge-entity entities x)
                     x))
                 routes))

(defmethod drp/router :reitit
  [{:keys [routes router-opts entities] :as config}]
  (let [routes (merge-entities entities routes)
        router (rc/router routes (merge {:compile coercion/compile-request-coercers
                                         :data    {:coercion rm/coercion}}
                                        router-opts))]
    (map->ReititRouter (merge {:routes routes
                               :router router}
                              (select-keys config [:on-no-path :on-no-route])))))
