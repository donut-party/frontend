(ns donut.frontend.sync.dispatch.ajax
  "Takes sync request and dispatch AJAX requests"
  (:require [re-frame.loggers :as rfl]
            [ajax.core :refer [GET HEAD POST PUT DELETE OPTIONS TRACE PATCH PURGE]]
            [ajax.transit :as at]
            [donut.frontend.sync.flow :as dsf]
            [clojure.set :as set]
            [cognitect.anomalies :as anom]))

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

(def segments-response-format
  {:read (at/transit-read-fn {})
   :description "Segments over Transit"
   :content-type ["application/st-segments+json"]})

(def fails
  {400 ::anom/incorrect
   401 ::anom/forbidden
   403 ::anom/forbidden
   404 ::anom/not-found
   405 ::anom/unsupported
   500 ::anom/fault
   503 ::anom/unavailable})

(defn adapt-req
  "Adapts the req opts as passed in by sync so that they'll work with
  cljs-ajax"
  [[method route-name opts :as req]]
  (if-let [path (:path opts)]
    [method route-name (-> opts
                           (assoc :uri path)
                           (cond-> (empty? (:params opts)) (dissoc :params)))]
    (rfl/console :warn
                 "Could not resolve route"
                 ::route-not-found {:req          req
                                    :route-params (:route-params opts)})))

(defn sync-dispatch-fn
  [{:keys [fail-map]
    :or   {fail-map fails}
    :as   global-opts}]
  (fn [req]
    (let [[method _res {:keys [uri] :as opts} :as req-sig] (adapt-req req)
          request-method                                   (get request-methods method)]

      (when-not req-sig
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
       (-> {:response-format segments-response-format}
           (merge global-opts opts)
           (assoc :handler       (fn [resp]
                                   ((dsf/sync-response-handler req)
                                    {:status        :success
                                     :response-data resp})))
           (assoc :error-handler (fn [resp]
                                   ((dsf/sync-response-handler req)
                                    (-> resp
                                        (assoc :status (get fail-map (:status resp) :fail))
                                        (set/rename-keys {:response :response-data}))))))))))

(defn system-sync-dispatch-fn
  [{:keys [global-opts]} _ _]
  (sync-dispatch-fn global-opts))
