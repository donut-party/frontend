(ns donut.frontend.events
  (:require
   [donut.compose :as dc]
   [donut.frontend.core.utils :as dcu]))


(def event-opts-path [:coeffects :event 1])

(defn ctx-db
  "db coeffect in interceptor context"
  [ctx]
  (get-in ctx [:coeffects :db]))

(defn triggered-callback-fx
  "returns a vector of dispatch fx's for callbacks that are triggered by a given event-name"
  [callback-opts cofx event-name]
  (let [callbacks (get-in callback-opts [::on event-name])
        callbacks (if (fn? callbacks) [callbacks] [])]
    (->> callbacks
         (reduce (fn [re-frame-events callback]
                   (cond
                     ;; TODO better handle functions
                     (fn? callback)         (let [x (callback cofx)]
                                              (if (sequential? x)
                                                (into re-frame-events x)
                                                re-frame-events))
                     (vector? callback)     (conj re-frame-events callback)
                     :else                  (throw (ex-info "unrecognized ::dfe/on callback form" {:callback callback}))))
                 [])
         (mapv (fn [re-frame-event]
                 ;; TODO handle when this is not mergeable
                 [:dispatch (update re-frame-event 1 #(if (or (map? %) (nil? %))
                                                        (dc/compose (::merge callback-opts) %)
                                                        %))])))))

(defn event-opts
  [ctx]
  (get-in ctx event-opts-path))

(def pre
  "Interceptor that lets you set preconditions in an event map. If ::pre is
  present in the event map and it returns falsey, the event stops."
  {:id     ::pre
   :before (fn [ctx]
             (let [pre-fns (-> ctx event-opts ::pre)]
               (assert (or (nil? pre-fns) (seqable? pre-fns)))
               (if (some #(not (% ctx)) pre-fns)
                 {:queue []}
                 ctx)))
   :after  identity})

(def xf
  "Intercepter that will transform the ctx"
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

(comment ;;notes
  ;; specify behavior composition for events
  ;; - data shape that's expected
  ;;
  ;; need to be able to do:
  ;; - not overwrite defaults, or what's there already
  ;; - merge the same value into every event
  ;; - control when events will be dispatched
  ;;
  ;; consider
  ;; - possible to make defaults opt-in by default? explicitly opt out?
  )
