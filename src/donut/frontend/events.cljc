(ns donut.frontend.events
  (:require
   [donut.frontend.core.utils :as dcu]))


(def event-opts-path [:coeffects :event 1])

(defn ctx-db
  "db coeffect in interceptor context"
  [ctx]
  (get-in ctx [:coeffects :db]))

(defn triggered-callback-fx
  [event-opts cofx event-name]
  (let [defaults   (get-in event-opts [::default :on event-name])
        callbacks  (get-in event-opts [::on event-name])]
    (->> callbacks
         (reduce (fn [re-frame-events callback]
                   (cond
                     (= ::default callback) (into re-frame-events defaults)
                     (fn? callback)         (into re-frame-events (callback cofx))
                     (vector? callback)     (conj re-frame-events callback)
                     :else                  (throw (ex-info "unrecognize ::dfe/on callback form" {:callback callback}))))
                 [])
         (mapv (fn [re-frame-event]
                 [:dispatch (update re-frame-event 1 dcu/>merge (::merge event-opts))])))))

(defn event-opts
  [ctx]
  (get-in ctx event-opts-path))

(def pre
  "Interceptor that lets you set preconditions in an event map. If ::pre is
  present in the event map and it returns falsey, the event stops."
  {:id     ::pre
   :before (fn [ctx]
             (let [pre-fns (-> ctx event-opts ::pre)]
               (if (some #(not (% ctx)) pre-fns)
                 {:queue []}
                 ctx)))
   :after  identity})

(def xf
  {:id ::xf
   :before (fn [ctx]
             (if-let [tx (-> ctx event-opts ::xf)]
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
