(ns donut.frontend.events
  (:require
   [donut.frontend.core.utils :as dcu]))


(def event-opts-path [:coeffects :event 1])

(defn ctx-db
  "db coeffect in interceptor context"
  [ctx]
  (get-in ctx [:coeffects :db]))

(defn compose-handler-fx
  "Builds up an fx vector. ex:

  (compose-handler-fx
   cofx
   [[:event-1 {}]
    (fn [cofx] [:event-2 {}])])
  "
  ([cofx callback-fx]
   (compose-handler-fx cofx callback-fx {}))
  ([cofx callback-fx common-opts]
   (if (fn? callback-fx)
     (callback-fx cofx)
     (->> callback-fx
          (mapv (fn [handler-event]
                  (if (fn? handler-event)
                    (handler-event cofx)
                    handler-event)))
          (filter identity)
          (mapv (fn [handler-event]
                  [:dispatch (update handler-event 1 dcu/>merge common-opts)]))))))

(defn compose-triggered-callback-fx
  "takes many \"candidates\" which are tuples of
  [map-that-provides-callback event-name]
  and creates a final vector of fx

  ex:
  (compose-triggered-callback-fx
   [[{::on {:success x, :fail y}} :success]
    [{::on {:success x, :fail y}} :success]])"
  ([cofx candidates]
   (compose-triggered-callback-fx cofx candidates {} []))
  ([cofx candidates common-opts]
   (compose-triggered-callback-fx cofx candidates common-opts []))
  ([cofx candidates common-opts fx]
   (reduce (fn [final-fx [m event-name]]
             (if-let [callback-fx (get-in m [::on event-name])]
               (into final-fx
                     (compose-handler-fx cofx callback-fx common-opts))
               final-fx))
           (or fx [])
           candidates)))

(defn event-opts
  [ctx]
  (get-in ctx event-opts-path))

(def pre
  "Interceptor that lets you set preconditions in an event map. If ::pre is
  present in the event map and it returns falsey, the event stops."
  {:id     ::pre
   :before (fn [ctx]
             (let [pre-fns (-> ctx event-opts ::pre)]
               (if (some #(% ctx) pre-fns)
                 {:queue []}
                 ctx)))
   :after  identity})

(def tx
  {:id ::tx
   :before (fn [ctx]
             (if-let [tx (-> ctx event-opts ::tx)]
               (reduce (fn [ctx' tx-fn]
                         (tx-fn ctx'))
                       ctx
                       tx)
               ctx))
   :after identity})

(defn merge-event-opts
  [ctx m]
  (update-in ctx event-opts-path merge m))

(defn opts-merge-db-vals
  "Transforms ctx's event-opts by merging in values looked up from the db

  ex:
  (opts-merge-db-vals ctx {:route-params (p/path :nav [:route :params])})"
  [ctx key->db-path]
  (let [db (ctx-db ctx)]
    (update-in ctx event-opts-path dcu/merge-retrieved-vals db key->db-path)))
