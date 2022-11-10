(ns donut.frontend.core.flow
  (:require
   [donut.frontend.path :as p]
   [donut.sugar.utils :as dsu]
   [donut.system :as ds]
   [re-frame.core  :as rf]
   [re-frame.db :as rfdb]
   [re-frame.loggers :as rfl])
  (:import
   #?(:cljs [goog.async Debouncer])))

(rf/reg-sub ::get-in
  (fn [db [_ path]]
    (get-in db path)))

(rf/reg-event-db ::assoc-in
  [rf/trim-v]
  (fn [db [path val]] (assoc-in db path val)))

(rf/reg-event-db ::dissoc-in
  [rf/trim-v]
  (fn [db [path]]
    (dsu/dissoc-in db path)))

(rf/reg-event-db ::update-in
  [rf/trim-v]
  (fn [db [path & args]]
    (apply update-in db path args)))

(rf/reg-event-db ::merge
  [rf/trim-v]
  (fn [db [m & [path]]]
    (if path
      (update-in db path merge m)
      (merge db m))))

(defn merge-entity
  ([db ent-type m]
   (merge-entity db ent-type :id m))
  ([db ent-type id-key m]
   (update-in db (p/path :entity [ent-type (id-key m)]) merge m)))

(rf/reg-event-db ::merge-entity
  [rf/trim-v]
  (fn [db [ent-type & args]]
    (apply merge-entity db ent-type args)))

(rf/reg-event-db ::deep-merge
  [rf/trim-v]
  (fn [db [m]]
    (dsu/deep-merge db m)))

(rf/reg-event-db ::toggle
  [rf/trim-v]
  (fn [db [path val alt-val]]
    (let [val (if (and (nil? val) (nil? alt-val)) true val)]
      (update-in db path #(dsu/toggle % val alt-val)))))

;; Toggles set inclusion/exclusion from set
(rf/reg-event-db ::set-toggle
  [rf/trim-v]
  (fn [db [path val]]
    (update-in db path dsu/set-toggle val)))

;;---
;; entities
;;---

(rf/reg-event-db ::remove-entity
  [rf/trim-v]
  (fn [db [entity-type id]]
    (update-in db [:entity entity-type] dissoc id)))

(rf/reg-sub ::entity
  (fn [db [_ ent-type id]]
    (p/get-path db :entity [ent-type id])))

(rf/reg-sub ::entities
  (fn [db [_ ent-type & [sort-key]]]
    (cond->> (p/get-path db :entity [ent-type])
      true     vals
      sort-key (sort-by sort-key))))

;;---
;; debouncing
;;---

;; TODO i don't like this atom :(
;; TODO dispose of debouncer
(def debouncers (atom {}))

(defn new-debouncer
  [interval dispatch]
  #?(:cljs (doto (Debouncer. rf/dispatch interval)
             (.fire dispatch))))

(rf/reg-fx ::debounce-dispatch
  (fn [debounce-effects]
    (doseq [{:keys [ms id dispatch] :as effect} (remove nil? debounce-effects)]
      (if (or (empty? dispatch) (not (number? ms)))
        (rfl/console :error "re-frame: ignoring bad :donut.frontend.core.flow/debounce-dispatch value:" effect)
        (if-let [debouncer ^Debouncer (get @debouncers id)]
          (.fire debouncer dispatch)
          (swap! debouncers assoc id (new-debouncer ms dispatch)))))))

;;---
;; system initialization
;;---

(rf/reg-event-fx ::start-system
  (fn [_ [_ config]]
    {::start-system config}))

(rf/reg-fx ::start-system
  (fn [config]
    (swap! rfdb/app-db assoc-in [:donut :system] (ds/signal config ::ds/start))))

(rf/reg-event-fx ::stop-system
  (fn [_ _]
    {:fx [[::stop-system]]}))

(rf/reg-fx ::stop-system
  (fn []
    (swap! rfdb/app-db update-in [:donut :system] #(ds/signal % ::ds/stop))))
