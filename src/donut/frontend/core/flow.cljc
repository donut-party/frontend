(ns donut.frontend.core.flow
  (:require [re-frame.core  :as rf]
            [re-frame.db :as rfdb]
            [re-frame.loggers :as rfl]
            [donut.frontend.handlers :as dh]
            [donut.sugar.utils :as dsu]
            [donut.system :as ds])
  (:import #?(:cljs [goog.async Debouncer])))

(dh/rr rf/reg-sub ::get-in
  (fn [db [_ path]]
    (get-in db path)))

#_(dh/rr rf/reg-event-db ::assoc-in
  [rf/trim-v]
  (fn [db [path val]] (assoc-in db path val)))

#_(dh/rr rf/reg-event-db ::merge
  [rf/trim-v]
  (fn [db [m & [path]]]
    (if path
      (update-in db path merge m)
      (merge db m))))

#_(defn merge-entity
  ([db ent-type m]
   (merge-entity db ent-type :id m))
  ([db ent-type id-key m]
   (update-in db (paths/full-path :entity ent-type (id-key m)) merge m)))

#_(dh/rr rf/reg-event-db ::deep-merge
  [rf/trim-v]
  (fn [db [m]]
    (dsu/deep-merge db m)))

#_(defn replace-entities
  "whereas deep merge will merge new entities with old, this replaces
  old entities with new."
  [db patches]
  (->> patches
       (filter #(= :entity (first %)))
       (map second)
       (reduce (fn [db patch]
                 (update-in db (paths/full-path :entity) (partial merge-with merge) patch))
               db)))

#_(dh/rr rf/reg-event-db ::replace-entities
  [rf/trim-v]
  (fn [db [patches]]
    (replace-entities db patches)))

#_(defn update-db
  "Takes a db and a vector of db-patches, and applies those patches to
  the db using the udpaters stored in
  [:sweet-tooth/system :sweet-tooth.frontend.core.flow/update-db]
  of the app-db."
  [db db-patches]
  {:pre [(vector? db-patches)]}
  (let [updaters (paths/get-path db :system ::update-db)]
    (reduce (fn [db [patch-key patch-val]]
              (if-let [updater (get updaters patch-key (get updaters :default))]
                (updater db patch-val)
                (throw (ex-info "no updater for patch" {:patch-key patch-key :patch-val patch-val :db-patches db-patches}))))
            db
            db-patches)))

#_(dh/rr rf/reg-event-db ::update-db
  [rf/trim-v]
  (fn [db [db-patches]]
    (update-db db db-patches)))

#_(defn db-patch-handle-entity
  [db db-patch]
  (let [entity-prefix (paths/prefix :entity)]
    (update db entity-prefix u/deep-merge db-patch)))

(dh/rr rf/reg-event-db ::toggle
  [rf/trim-v]
  (fn [db [path val alt-val]]
    (let [val (if (and (nil? val) (nil? alt-val)) true val)]
      (update-in db path #(dsu/toggle % val alt-val)))))

;; Toggles set inclusion/exclusion from set
(dh/rr rf/reg-event-db ::set-toggle
  [rf/trim-v]
  (fn [db [path val]]
    (update-in db path dsu/set-toggle val)))

#_(dh/rr rf/reg-event-db ::dissoc-in
  [rf/trim-v]
  (fn [db [path]]
    (dsu/dissoc-in db path)))

#_(dh/rr rf/reg-event-db ::remove-entity
  [rf/trim-v]
  (fn [db [entity-type id]]
    (update-in db [:entity entity-type] dissoc id)))

;; debounce dispatches
;; TODO i don't like this atom :(
;; TODO dispose of debouncer
(def debouncers (atom {}))

(defn new-debouncer
  [interval dispatch]
  #?(:cljs (doto (Debouncer. rf/dispatch interval)
             (.fire dispatch))))

(dh/rr rf/reg-fx ::debounce-dispatch
  (fn [debounce-effects]
    (doseq [{:keys [ms id dispatch] :as effect} (remove nil? debounce-effects)]
      (if (or (empty? dispatch) (not (number? ms)))
        (rfl/console :error "re-frame: ignoring bad :donut.frontend.core.flow/debounce-dispatch value:" effect)
        (if-let [debouncer ^Debouncer (get @debouncers id)]
          (.fire debouncer dispatch)
          (swap! debouncers assoc id (new-debouncer ms dispatch)))))))

;; system initialization
(rf/reg-event-fx ::init-system
  (fn [_ [_ config]]
    {::init-system config}))

(rf/reg-fx ::init-system
  (fn [config]
    (swap! rfdb/app-db merge {:donut/system (ds/signal config :start)})))
