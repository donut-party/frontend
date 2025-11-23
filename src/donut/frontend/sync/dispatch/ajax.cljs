(ns donut.frontend.sync.dispatch.ajax
  "Takes sync request and dispatch AJAX requests"
  (:require
   [ajax.core :refer [GET HEAD POST PUT DELETE OPTIONS TRACE PATCH PURGE]]
   [clojure.set :as set]
   [cognitect.anomalies :as anom]
   [donut.frontend.encoding :as denc]
   [donut.frontend.sync.flow :as dsf]
   [re-frame.loggers :as rfl]))

(def request-methods
  {:get     GET
   :put     PUT
   :post    POST
   :delete  DELETE
   :options OPTIONS
   :trace   TRACE
   :patch   PATCH
   :purge   PURGE
   :head    HEAD})

(def fails
  {400 ::anom/incorrect
   401 ::anom/forbidden
   403 ::anom/forbidden
   404 ::anom/not-found
   405 ::anom/unsupported
   422 ::anom/incorrect
   500 ::anom/fault
   503 ::anom/unavailable})

(defn adapt-req
  "Adapts the req opts as passed in by sync so that they'll work with
  cljs-ajax"
  [{:keys [params path] :as req}]
  (if path
    (-> req
        (assoc :uri path)
        (cond-> (empty? params) (dissoc :params)))
    (rfl/console :warn
                 "Could not resolve route"
                 ::route-not-found {:req          req
                                    :route-params (:route-params req)})))

(defn sync-dispatch-fn
  [{:keys [fail-map]
    :or   {fail-map fails}
    :as   global-opts}]
  (fn [req]
    (let [{:keys [method uri] :as adapted} (adapt-req req)
          request-method                   (get request-methods method)]

      (when-not adapted
        (rfl/console :error
                     "could not find route for request"
                     ::no-route-found
                     {:req req})
        (throw (js/Error. "Invalid request: could not find route for request")))

      (when-not request-method
        (rfl/console :error
                     (str "request method did not map to an HTTP request function. valid methods are " (keys request-methods))
                     ::ajax-dispatch-no-request-method
                     {:req    req
                      :method method})
        (throw (js/Error. "Invalid request: no request method found")))

      ((get request-methods method)
       uri
       (-> (merge global-opts req)
           (assoc :handler       (fn [resp]
                                   ((dsf/sync-response-handler req)
                                    {:status        :success
                                     :response-data resp})))
           (assoc :response-format (ajax.core/transit-response-format {:handlers denc/read-handlers}))
           (assoc :error-handler (fn [resp]
                                   ((dsf/sync-response-handler req)
                                    (-> resp
                                        (assoc :status (get fail-map (:status resp) :fail))
                                        (set/rename-keys {:response :response-data}))))))))))

(defn system-sync-dispatch-fn
  [{:keys [:donut.system/config]}]
  (sync-dispatch-fn (:global-opts config)))
