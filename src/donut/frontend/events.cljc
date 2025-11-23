(ns donut.frontend.events)


(def event-opts-path [:coeffects :event 1])

(defn ctx-db
  "db coeffect in interceptor"
  [ctx]
  (get-in ctx [:coeffects :db]))

(defn compose-handler-fx
  "Builds up an fx vector"
  [cofx callback-fx fx]
  (if (fn? callback-fx)
    (callback-fx cofx)
    (->> callback-fx
         (mapv (fn [handler-event]
                 (if (fn? handler-event)
                   (handler-event cofx)
                   handler-event)))
         (filter identity)
         (mapv (fn [handler-event]
                 [:dispatch handler-event]))
         (into (vec fx)))))

(defn compose-triggered-callback-fx
  "takes many \"candidates\" which are tuples of
  [map-that-provides-callback event-name]
  and creates a final vector of fx"
  ([cofx candidates]
   (compose-triggered-callback-fx cofx candidates []))
  ([cofx candidates fx]
   (reduce (fn [final-fx [m event-name]]
             (if-let [callback-fx (get-in m [::on event-name])]
               (compose-handler-fx cofx callback-fx final-fx)
               final-fx))
           fx
           candidates)))

(defn event-opts
  [ctx]
  (get-in ctx event-opts-path))

(def pre
  "Interceptor that lets you set preconditions in an event map. If ::pre is
  present in the event map and it returns falsey, the event stops."
  {:id     ::pre
   :before (fn [{:keys [coeffects]
                 :as ctx}]
             (let [pre-fn (-> ctx event-opts ::pre)]
               (if (or (not pre-fn) (pre-fn coeffects))
                 ctx
                 {:queue []})))
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
  [ctx m]
  (let [db    (ctx-db ctx)
        eopts (event-opts ctx)]
    (assoc-in ctx event-opts-path
              (reduce-kv (fn [eopts' k db-path]
                           (assoc eopts' k (get-in db db-path)))
                         eopts
                         m))))
