(ns donut.frontend.nav.flow
  "Adapted from Accountant, https://github.com/venantius/accountant
  Accountant is licensed under the EPL v1.0."
  (:require [goog.events :as events]
            [goog.events.EventType]
            [re-frame.core :as rf]
            [re-frame.loggers :as rfl]
            [medley.core :as medley]
            [donut.frontend.nav.accountant :as accountant]
            [donut.frontend.path :as p]
            [donut.frontend.core.utils :as dcu]
            [donut.frontend.handlers :as dh]
            [donut.frontend.nav.utils :as dnu]
            [donut.frontend.sync.flow :as dsf]
            [donut.frontend.routes :as dfr]
            [donut.frontend.routes.protocol :as drp]
            [donut.sugar.utils :as dsu])
  (:import [goog.history Html5History]))

(defn- handle-unloading
  []
  (let [listener (fn [e] (rf/dispatch-sync [::before-unload e]))]
    (.addEventListener js/window "beforeunload" listener)
    listener))

(defn init-handler
  "Configures accountant, window unloading, keeps track of event
  handlers for system teardown"
  [{:keys [router
           dispatch-route-handler
           reload-same-path?
           check-can-unload?
           global-lifecycle]
    :as _config}
   & _]
  (let [history      (accountant/new-history)
        nav-handler  (fn [path] (rf/dispatch [dispatch-route-handler path]))
        update-token (fn [relative-href title] (rf/dispatch [::update-token relative-href :set title]))
        path-exists? #(drp/route router %)]
    {:router           router
     :history          history
     :global-lifecycle global-lifecycle
     :listeners        (cond-> {:document-click (accountant/prevent-reload-on-known-path history
                                                                                         path-exists?
                                                                                         reload-same-path?
                                                                                         update-token)
                                :navigate       (accountant/dispatch-on-navigate history nav-handler)}
                         check-can-unload? (assoc :before-unload (handle-unloading)))}))

(defn halt-handler!
  "Teardown HTML5 history navigation.

  Undoes all of the stateful changes, including unlistening to events,
  that are setup when init'd"
  [handler & _]
  (.dispose ^Html5History (:history handler))
  (doseq [key (vals (select-keys (:listeners handler) [:document-click :navigate]))]
    (events/unlistenByKey key))
  (when-let [before-unload (get-in handler [:listeners :before-unload])]
    (.removeEventListener js/window "beforeunload" before-unload)))

(defn- navigate-handler
  [{:keys [db] :as _cofx} [path query]]
  (let [{:keys [history]} (p/get-path db :donut-component [:nav-handler])
        token             (.getToken history)
        query-string      (dcu/params-to-str (reduce-kv (fn [valid k v]
                                                          (if v
                                                            (assoc valid k v)
                                                            valid))
                                                        {}
                                                        query))
        with-params       (if (empty? query-string)
                            path
                            (str path "?" query-string))]
    (if (= token with-params)
      {:dispatch [::update-token with-params :replace]}
      {:dispatch [::update-token with-params :set]})))

;; Used for synthetic navigation events
(dh/rr rf/reg-event-fx ::navigate
  [rf/trim-v]
  navigate-handler)

(dh/rr rf/reg-event-fx ::navigate-route
  [rf/trim-v]
  (fn [{:keys [db] :as cofx} [route route-params query-params]]
    (let [router (p/get-path db :donut-component [:frontend-router])]
      (when-let [path (drp/path router route route-params query-params)]
        (navigate-handler cofx [path])))))

;; ------
;; Route change handlers
;; ------
(defn can-change-route?
  [db scope existing-route new-route]
  ;; are we changing the entire route or just the params?
  (let [route-change-checks (-> (merge (select-keys (:lifecycle existing-route) [:can-change-params? :can-exit?])
                                       (select-keys (:lifecycle new-route) [:can-enter?]))
                                (select-keys (case scope
                                               :route  [:can-change-params? :can-exit? :can-enter?]
                                               :params [:can-change-params?])))
        check-failures      (medley/filter-vals (fn [lifecycle-fn]
                                                  (and lifecycle-fn (not (lifecycle-fn db
                                                                                       existing-route
                                                                                       new-route))))
                                                route-change-checks)]
    (or (empty? check-failures)
        (rfl/console :debug
                     ::prevented-route-change
                     {:check-failures (set (keys check-failures))}))))

(def process-route-change
  "Intercepor that interprets new route, adding a ::route-change coeffect"
  {:id     ::process-route-change
   :before (fn [{{:keys [db event]} :coeffects
                 :as                ctx}]
             (let [global-lifecycle (p/get-path db :donut-component [:nav-global-lifecycle])
                   router           (p/get-path db :donut-component [:frontend-router])
                   path             (get event 1)
                   new-route        (or (drp/route router path)
                                        (drp/route router ::not-found))
                   existing-route   (p/get-path db :nav [:route])
                   scope            (if (= (:route-name new-route)
                                           (:route-name existing-route))
                                      :params
                                      :route)]
               (assoc-in ctx [:coeffects ::route-change]
                         {:can-change-route? (can-change-route? db scope existing-route new-route)
                          :scope             scope
                          :old-route         existing-route
                          :new-route         new-route
                          :global-lifecycle  global-lifecycle})))
   :after identity})

;; ------
;; dispatch route
;; ------
(defn compose-route-lifecycle
  [cofx lifecycle hook-names fx]
  (let [{:keys [old-route new-route global-lifecycle]} (::route-change cofx)]
    (->> hook-names
         (map (fn [hook-name]
                (when-let [hook (or (and (contains? lifecycle hook-name)
                                         (hook-name lifecycle))
                                    (hook-name global-lifecycle))]
                  (if (fn? hook)
                    (hook cofx old-route new-route)
                    hook))))
         (filter identity)
         (into fx))))

(defn route-effects
  "Handles all route lifecycle effects"
  [cofx]
  (let [{:keys [scope old-route new-route]} (::route-change cofx)]
    (cond->> []
      (= scope :route) (compose-route-lifecycle cofx (:lifecycle old-route) [:before-exit :exit :after-exit])
      (= scope :route) (compose-route-lifecycle cofx (:lifecycle new-route) [:before-enter :enter :after-enter])
      true             (compose-route-lifecycle cofx (:lifecycle new-route) [:before-param-change :param-change :after-param-change])
      ;; TODO this limits routes to only ever being able to dispatch, which
      ;; isn't necessarily what we want.
      true             (map (fn [dispatch] [:dispatch dispatch]))
      true             (into [[:dispatch-later {:ms 0 :dispatch [::nav-loaded]}]]))))

(defn change-route-fx
  "Composes all effects returned by lifecycle methods"
  ([cofx _] (change-route-fx cofx))
  ([{:keys [db] :as cofx}]
   (let [{:keys [can-change-route?] :as route-change-cofx} (::route-change cofx)]
     (when can-change-route?
       (let [db (-> db
                    (assoc-in (p/path :nav [:route]) (:new-route route-change-cofx))
                    (assoc-in (p/path :nav [:state]) :loading))
             updated-cofx (assoc cofx :db db)]
         {:db db
          :fx (route-effects updated-cofx)})))))

;; Default handler for new routes
(dh/rr rf/reg-event-fx ::dispatch-route
  [process-route-change]
  change-route-fx)

(dh/rr rf/reg-event-db ::nav-loaded
  [rf/trim-v]
  (fn [db _]
    (assoc-in db (p/path :nav [:state]) :loaded)))

;; ------
;; dispatch current
;; ------

(def add-current-path
  {:id     ::add-current-path
   :before (fn [ctx]
             (let [path  (-> js/window .-location .-pathname)
                   query (-> js/window .-location .-search)
                   hash  (-> js/window .-location .-hash)]
               (assoc-in ctx [:coeffects :event 1] (str path query hash))))
   :after  identity})

(dh/rr rf/reg-event-fx ::dispatch-current
  [add-current-path process-route-change]
  change-route-fx)

;; force the param change and enter lifecycle methods of the current
;; route to run again.
(dh/rr rf/reg-event-fx ::perform-current-lifecycle
  []
  (fn [{:keys [db] :as cofx} _]
    (let [current-route (get-in db (p/path :nav [:route]))]
      (change-route-fx
       (assoc cofx
              ::route-change
              {:can-change-route? true
               :scope             :route
               :new-route         current-route
               :global-lifecycle  (p/get-path db :donut-component [:nav-handler :global-lifecycle])})))))

;; ------
;; update token
;; ------

;; TODO figure out when this is actually used...
(dh/rr rf/reg-event-fx ::update-token
  [process-route-change]
  (fn [{:keys [db] :as cofx} [_ relative-href op title]]
    (when-let [fx (change-route-fx cofx)]
      (update fx :fx conj
              [::update-token
               {:history       (p/get-path db :donut-component [:nav-handler :history])
                :relative-href relative-href
                :title         title
                :op            op}]))))

(dh/rr rf/reg-fx ::update-token
  (fn [{:keys [op history relative-href title]}]
    (reset! accountant/app-updated-token? true)
    (if (= op :replace)
      (. history (replaceToken relative-href title))
      (. history (setToken relative-href title)))))

;; ------
;; check can unload
;; ------

(dh/rr rf/reg-event-fx ::before-unload
  []
  (fn [{:keys [db] :as cofx} [_ before-unload-event]]
    (let [existing-route                          (p/get-path db :nav :route)
          {:keys [can-unload?]
           :or   {can-unload? (constantly true)}} (when existing-route (:lifecycle existing-route))]
      (when-not (can-unload? db)
        {::cancel-unload before-unload-event}))))

(rf/reg-fx ::cancel-unload
  (fn [before-unload-event]
    (.preventDefault before-unload-event)
    (set! (.-returnValue before-unload-event) "")))


;; ------
;; nav-buffer
;; ------
;;
;; Sometimes you want to state to get cleared whenever a route changes or params
;; change. This is the place to do that

(defn assoc-in-buffer
  [db path val]
  (assoc-in db (p/path :nav-buffer path) val))

(rf/reg-sub ::buffer
  (fn [db [_ path]]
    (p/get-path db :nav-buffer path)))

(dh/rr rf/reg-event-db ::assoc-in-buffer
  [rf/trim-v]
  (fn [db [path val]]
    (assoc-in-buffer db path val)))

(dh/rr rf/reg-event-db ::clear-buffer
  [rf/trim-v]
  (fn [db [path]]
    (assoc-in-buffer db path nil)))

;; ------
;; nav flow components
;; ------

(def default-global-lifecycle
  {:before-exit         nil
   :after-exit          nil
   :before-enter        (constantly [::clear-buffer [:route]])
   :after-enter         nil
   :before-param-change (constantly [::clear-buffer [:params]])
   :after-param-change  nil})

;; ------
;; subscriptions
;; ------

(defn nav
  [db]
  (p/get-path db :nav))

(rf/reg-sub ::nav
  (fn [db _]
    (nav db)))

(rf/reg-sub ::route
  :<- [::nav]
  (fn [nav _] (:route nav)))

(rf/reg-sub ::nav-state
  :<- [::nav]
  (fn [nav _] (:state nav)))

(rf/reg-sub ::params
  :<- [::nav]
  (fn [nav _] (:params (:route nav))))

(rf/reg-sub ::routed-component
  :<- [::route]
  (fn [route [_ path]]
    (get-in route (dsu/flatv :components path))))

(rf/reg-sub ::route-name
  :<- [::route]
  (fn [route _] (:route-name route)))

(rf/reg-sub ::routed-entity
  (fn [db [_ entity-key param]]
    (dnu/routed-entity db entity-key param)))

;; uses routed path params to get sync state
(rf/reg-sub ::route-sync-state
  (fn [db [_ path-prefix]]
    (dsf/sync-state db (conj path-prefix (-> db nav :route :params)))))

;; ------
;; form interactions
;; ------

(comment
  ;; TODO move this to form namespace
  (dh/rr rf/reg-event-db ::initialize-form-with-routed-entity
    [rf/trim-v]
    (fn [db [form-path entity-key param-key form-opts]]
      (dnu/initialize-form-with-routed-entity db form-path entity-key param-key form-opts))))

(rf/reg-event-fx ::navigate-to-synced-entity
  [rf/trim-v]
  (fn [_ [route-name ctx]]
    {:dispatch [::navigate (dfr/path route-name (dsf/single-entity ctx))]}))
