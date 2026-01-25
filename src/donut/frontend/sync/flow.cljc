(ns donut.frontend.sync.flow
  "Sync provides tools for syncing data across nodes, and for tracking
  the state of sync requests.

  The term 'sync' is used instead of AJAX"
  (:require
   [cognitect.anomalies :as anom]
   [donut.compose :as dc]
   [donut.frontend.events :as dfe]
   [donut.frontend.failure.flow :as dfaf]
   [donut.frontend.path :as p]
   [donut.frontend.routes :as dfr]
   [donut.frontend.routes.protocol :as drp]
   [donut.frontend.sync.response :as dfsr]
   [donut.sugar.utils :as dsu]
   [medley.core :as medley]
   [re-frame.core :as rf]
   [re-frame.loggers :as rfl]))

;;---
;; utils
;;---

(defn sync-key
  "returns a 'normalized' req key for a request. uses :donut.sync/key if present,
  otherwise uses a best-guess default of a projection of the request map that's
  likely to meaningfully distinguish different requests you want to track.
  `:donut.sync/key`."
  [{:keys [::req] :as sync}]
  (or (::sync-key sync)
      (let [req-id (dfr/req-id req)]
        (-> req
            (select-keys [:method :route-name])
            (assoc :route-params req-id)))))

;;--------------------
;; specs
;;--------------------

(def ReqMethod keyword?)
(def RouteName keyword?)
(def URI string?)
(def Params map?)
(def RouteParams Params)
(def QueryParams Params)

(def URIReq
  [:map
   [:method ReqMethod]
   [:uri URI]
   [:query-params {:optional true} QueryParams]
   [:params {:optional true} Params]])

(def RoutedReq
  [:map
   [:method ReqMethod]
   [:route-name {:optional true} RouteName]
   [:route-params {:optional true} RouteParams]
   [:query-params {:optional true} QueryParams]
   [:params {:optional true} Params]])

(def Req
  [:or
   RoutedReq
   URIReq])

(def DispatchFn fn?)
(def DispatchSync
  [:map
   [:req Req]
   [:dispatch-fn DispatchFn]])

;;------
;; registrations
;;------

(defn sync-state
  [db req]
  (p/get-path db :reqs [(sync-key req) :state]))

(rf/reg-sub ::req
  (fn [db [_ req]]
    (p/get-path db :reqs [(sync-key req)])))

(rf/reg-sub ::sync-state
  (fn [db [_ req comparison]]
    (let [state (sync-state db req)]
      (if comparison
        (isa? state comparison)
        state))))

(defn sync-state-signal
  [[_ req]]
  (rf/subscribe [::sync-state req]))

(rf/reg-sub ::sync-active?
  sync-state-signal
  (fn [sync-state]
    (= sync-state :active)))

(rf/reg-sub ::sync-success?
  sync-state-signal
  (fn [sync-state]
    (= sync-state :success)))

(rf/reg-sub ::sync-fail?
  sync-state-signal
  (fn [sync-state]
    (= sync-state :fail)))

;; Used to find, e.g. all requests like [:get :topic] or [:post :host]
(rf/reg-sub ::sync-state-q
  (fn [db [_ query]]
    (medley/filter-keys (partial dsu/projection? query) (get-in db [:donut :reqs]))))

(rf/reg-event-db ::default-sync-success
  [rf/trim-v]
  (fn [db [sync-lifecycle]]
    (dfsr/handle-sync-response-data db sync-lifecycle)))

(rf/reg-event-fx ::default-sync-fail
  [rf/trim-v]
  (fn [_cofx [{:keys [::req] :as _sync-lifecycle}]]
    ;; TODO possibly allow failed responses to carry data
    (rfl/console :log "sync failed" req)
    {:dispatch [::dfaf/add-failure req]}))

(rf/reg-event-fx ::default-sync-unavailable
  [rf/trim-v]
  (fn [_cofx [{:keys [::req]}]]
    (let [sync-info {:req (into [] (take 2 req))}]
      (rfl/console :warn "Service unavailable. Try `(dev) (start)` in your REPL." sync-info)
      {:dispatch [::dfaf/add-failure [:sync sync-info]]})))

;;--------------------
;; request tracking
;;--------------------

(defn track-new-request
  "Adds a request's state te the app-db and increments the active request
  count"
  [db req]
  (-> db
      (update-in (p/reqs-path [(sync-key req)])
                 merge
                 {:state            :active
                  :active-nav-route (p/get-path db :nav [:route])})
      (update ::active-request-count (fnil inc 0))))

(defn remove-req
  [db req]
  (update-in db [:donut :reqs] dissoc (sync-key req)))

;;------
;; dispatch handler wrappers
;;------
(defn sync-finished
  "Update sync bookkeeping"
  [db {:keys [::req ::resp]}]
  (-> db
      (assoc-in (p/reqs-path [(sync-key req) :state]) (:status resp))
      (update ::active-request-count dec)))

(rf/reg-event-fx ::handle-sync-response
  [rf/trim-v]
  (fn [{:keys [db] :as cofx} [{:keys [::resp] :as sync}]]
    (let [status (if (= (:status resp) :success) :success :fail)]
      {:db (sync-finished db sync)
       :fx (dfe/triggered-callback-fx
            (update sync ::dfe/merge merge sync)
            cofx
            status)})))

(rf/reg-event-fx ::fn-response-handler
  [rf/trim-v]
  (fn [_ [f ctx]]
    {::fn-response-handler [f ctx]}))

(rf/reg-fx ::fn-response-handler
  (fn [[f ctx]]
    (f ctx)))

(defn sync-response-handler
  "Used by sync implementations (e.g. ajax) to create a response handler"
  [sync]
  (fn anon-sync-response-handler [resp]
    (rf/dispatch [::handle-sync-response (assoc sync ::resp resp)])))

;;-----------------------
;; dispatch sync requests
;;-----------------------

(def default-handlers
  {:success           [[::default-sync-success]]
   :fail              [[::default-sync-fail]]
   ::anom/unavailable [[::default-sync-unavailable]]})

(def default-callbacks
  {::dfe/on default-handlers})

;;---
;; helpers

(defn adapt-req
  "Makes sure a path is findable from req and adds it"
  [{::keys [req] :as sync} router]
  (when-let [path (drp/path router req)]
    (assoc-in sync [::req :path] path)))

(defn ctx-req
  "Retrieve request within interceptor"
  [ctx]
  (get-in ctx [:coeffects :event 1]))

(defn update-ctx-sync
  [ctx f & args]
  (apply update-in ctx [:coeffects :event 1] f args))

(defn ctx-sync-state
  [ctx]
  (sync-state (dfe/ctx-db ctx) (ctx-req ctx)))

;;---
;; sync interceptors
;;---

;;---
;; populate sync with path data

(def set-request-defaults
  "Populates a default sync-dispatch-fn from the configured system, allowing
  overrides"
  {:id     ::sync-dispatch-fn
   :before (fn [ctx]
             (update-ctx-sync ctx dc/>compose {::dfe/on           default-handlers
                                               ::sync-dispatch-fn (-> ctx
                                                                      (dfe/ctx-db)
                                                                      (p/get-path :donut-component)
                                                                      :sync-dispatch-fn)}))
   :after  identity})

(def add-auth-header
  "Adds the 'Authorization' http header when there's an auth token present"
  {:id     ::add-auth-header
   :before (fn [ctx]
             (if-let [auth-token (p/get-path (dfe/ctx-db ctx) :auth [:auth-token])]
               (update-ctx-sync ctx update-in [::req :headers "Authorization"] #(or % auth-token))
               ctx))
   :after  identity})

(def sync-interceptors
  [set-request-defaults
   add-auth-header
   dfe/xf
   dfe/pre
   rf/trim-v])

;;---
;; sync
;;---

(defn sync-event-fx
  "Transforms sync events adding defaults and other options needed for
  the `::dispatch-sync` effect handler to perform a sync.

  returns an effect map of:
  a) updated db to track a sync request
  b) ::dispatch-sync effect, to be handled by the ::dispatch-sync
  effect handler"
  [{:keys [db] :as _cofx} sync]
  (let [{:keys [sync-router]} (p/get-path db :donut-component)
        adapted-req           (adapt-req sync sync-router)]
    (if adapted-req
      {:db             (track-new-request db adapted-req)
       ::dispatch-sync adapted-req}
      (do (rfl/console :warn "sync router could not match req"
                       {:req (-> sync
                                 ::req
                                 (update 2 select-keys [:params :route-params :query-params :data]))})
          {:db db}))))

;;---
;; handlers

;; The core event handler for syncing
(rf/reg-event-fx ::sync
  sync-interceptors
  (fn [cofx [req]]
    (sync-event-fx cofx req)))

;; The effect handler that actually performs a sync
(rf/reg-fx ::dispatch-sync
  (fn [{::keys [sync-dispatch-fn] :as sync}]
    (sync-dispatch-fn sync)))

;;---------------
;; sync events
;;---------------

(doseq [event-name [::get ::put ::post ::delete ::patch]]
  (let [method (keyword (name event-name))]
    (rf/reg-event-fx event-name
      sync-interceptors
      (fn [cofx [sync]]
        (sync-event-fx cofx (assoc-in sync [::req :method] method))))))

;;---------------
;; response helpers
;;---------------

(defn single-entity
  [sync-response]
  (get-in sync-response [::resp :response-data]))

;;---------------
;; sync subs
;;---------------

(defn sync-subs
  [sync-key]
  {:sync-state    (rf/subscribe [::sync-state sync-key])
   :sync-active?  (rf/subscribe [::sync-state sync-key :active])
   :sync-success? (rf/subscribe [::sync-state sync-key :success])
   :sync-fail?    (rf/subscribe [::sync-state sync-key :fail])})

;;---------------
;; sync req data handlers
;;---------------

(defn remove-reqs
  [db key-filter value-filter]
  (let [key-filter   (or key-filter (constantly true))
        value-filter (or value-filter (constantly true))]
    (update db (p/path :reqs [])
            (fn [req-map]
              (->> req-map
                   (remove (fn [[k v]] (and (key-filter k) (value-filter v))))
                   (into {}))))))

(rf/reg-event-db ::remove-reqs
  [rf/trim-v]
  (fn [db [key-filter value-filter]]
    (remove-reqs db key-filter value-filter)))

;; remove all sync reqs dispatched while `route` was active
;; with a method found in set `methods`
(rf/reg-event-db ::remove-reqs-by-route-and-method
  [rf/trim-v]
  (fn [db [route methods]]
    (remove-reqs db
                 #(methods (first %))
                 #(= (:route-name route)
                     (get-in % [:active-route :route-name])))))

;;---------------
;; sync tx helpers
;;---------------

(defn use-current-route-params
  [ctx]
  (assoc-in
   ctx
   [:coeffects :event 1 ::req :route-params]
   (get-in (dfe/ctx-db ctx) (p/path :nav [:route :params]))))

(defn not-active
  [ctx]
  (not= :active (ctx-sync-state ctx)))

;;---
;; sync event helpers
;;---

(defn response-data
  [ctx]
  (-> ctx ::resp :response-data))
