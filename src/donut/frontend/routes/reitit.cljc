(ns donut.frontend.routes.reitit
  (:require
   [re-frame.loggers :as rfl]
   [reitit.core :as rc]
   [reitit.frontend :as reif]
   [reitit.coercion :as coercion]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.routes.protocol :as drp]
   [clojure.set :as set]))

(defn on-no-path-default
  [name match route-params]
  (let [required (get match :required)]
    ;; TODO update this to be more specific. does it not exist? is it missing params?
    (rfl/console :warn
                 "reitit could not generate path. route might not exist, or might not have required params."
                 {:route-name   name
                  :route-params (select-keys route-params required)
                  :required     required
                  :match        (-> match
                                    (dissoc :data :required)
                                    (update :path-params select-keys required))})))

(defn on-no-route-default
  [path-or-name route-params query-params]
  (rfl/console :warn
               "reitit could not match route"
               {:route-args [path-or-name route-params query-params]}))

(def config-defaults
  {:use         :reitit
   :on-no-path  on-no-path-default
   :on-no-route on-no-route-default})

(defrecord ReititRouter [routes router on-no-path on-no-route]
  drp/Router
  (drp/path
    [this name]
    (drp/path this name {} {}))
  (drp/path
    [this name route-params]
    (drp/path this name route-params {}))
  (drp/path
    [_this name route-params query-params]
    (let [{{:keys [prefix]} :data :as match} (rc/match-by-name router name route-params)]
      (if (and match (not (:required match)))
        (cond-> match
          true                     (rc/match->path)
          (not-empty query-params) (str "?" (dcu/params-to-str query-params))
          prefix                   (as-> p (str prefix  p)))
        (when on-no-path
          (on-no-path name match route-params)
          nil))))

  (drp/req-id
    [this name]
    (drp/req-id this name {}))
  (drp/req-id
    [_this name opts]
    (when (and (some? opts) (not (map? opts)))
      (rfl/console :error "req-id opts should be a map" {:opts opts}))
    (let [params (or (:route-params opts)
                     (:params opts)
                     opts)]
      (select-keys params (:required (rc/match-by-name router name)))))

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

(defmethod drp/router :reitit
  [{:keys [routes router-opts] :as config}]
  (let [router (rc/router routes (merge {:compile coercion/compile-request-coercers}
                                        router-opts))]
    (map->ReititRouter (merge {:routes      routes
                               :router      router}
                              (select-keys config [:on-no-path :on-no-route])))))
