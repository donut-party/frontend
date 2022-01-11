(ns donut.frontend.sync.flow
  "Sync provides tools for syncing data across nodes, and for tracking
  the state of sync requests.

  The term 'sync' is used instead of AJAX"
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.core.compose :as stcc]
            [sweet-tooth.frontend.routes :as stfr]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.failure.flow :as stfaf]
            [sweet-tooth.frontend.specs :as ss]
            [integrant.core :as ig]
            [medley.core :as medley]
            [clojure.walk :as walk]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as log]
            [clojure.spec.alpha :as s]
            [cognitect.anomalies :as anom]))

(doseq [t [::anom/incorrect
           ::anom/forbidden
           ::anom/not-found
           ::anom/unsupported
           ::anom/fault
           ::anom/unavailable
           :fail]]
  (derive t ::fail))

;;--------------------
;; specs
;;--------------------

(def ReqMethod keyword?)
(def RouteName keyword?)
(def Params map?)
(def RouteParams Params)
(def QueryParams Params)
(def ReqOpts [:map
              [:route-params {:optional true} RouteParams]
              [:query-params {:optional true} QueryParams]
              [:params {:optional true} Params]])

(def Req [:cat
          [:req-method ReqMethod]
          [:route-name RouteName]
          [:req-opts ReqOpts]])

(def DispatchFn fn?)
(def DispatchSync [:map
                   [:req Req]
                   [:dispatch-fn DispatchFn]])

;;--------------------
;; request tracking
;;--------------------
(defn req-path
  "returns a 'normalized' req path for a request.

  normalized in the sense that when it comes to distinguishing requests
  in order to track them, some of the variations between requests are
  significant, and some aren't.

  The first two elements of the request, `method` and `route`, are
  always significant. Where things get tricky is with `opts`. We don't
  want to use `opts` itself because the variation would lead to
  \"identical\" requests being treated as separate, so we use
  `stfr/req-id` to select a subset of opts to distinguish reqs"
  [[method route opts]]
  (let [path (or (::req-path opts)
                 (let [req-id (or (::req-id opts) (stfr/req-id route opts))]
                   (if (empty? req-id)
                     [method route]
                     [method route req-id])))]
    (when (contains? (:debug opts) ::req-path)
      (log/info ::req-path
                {:opts-req-path (::req-path opts)
                 :route-name    route
                 :req-path      path}))
    path))

(defn track-new-request
  "Adds a request's state te the app-db and increments the active request
  count"
  [db req]
  (-> db
      (update-in [::reqs (req-path req)] merge {:state        :active
                                                :active-route (paths/get-path db :nav :route)})
      (update ::active-request-count (fnil inc 0))))

(defn remove-req
  [db req]
  (update db ::reqs dissoc (req-path req)))

;;------
;; dispatch handler wrappers
;;------
(defn sync-finished
  "Update sync bookkeeping"
  [db [_ req resp]]
  (-> db
      (assoc-in [::reqs (req-path req) :state] (:status resp))
      (update ::active-request-count dec)))

(sth/rr rf/reg-event-db ::sync-finished
  []
  sync-finished)

(defn sync-response-handler
  "Returns a function to handle sync responses"
  [req]
  (fn [{:keys [status] :as resp}]
    (let [{:keys [on] :as rdata} (get req 2)
          $ctx                   (assoc (get rdata :$ctx {})
                                        :resp resp
                                        :req  req)
          composed-fx            (stcc/compose-fx (get on status (get on :fail)))]
      (rf/dispatch [::stcc/compose-dispatch
                    [[::sync-finished req resp]
                     (walk/postwalk (fn [x] (if (= x :$ctx) $ctx x))
                                    composed-fx)]]))))

;;------
;; registrations
;;------
(defn sync-state
  [db req]
  (get-in db [::reqs (req-path req) :state]))

(rf/reg-sub ::req
  (fn [db [_ req]]
    (get-in db [::reqs (req-path req)])))

(rf/reg-sub ::sync-state
  (fn [db [_ req comparison]]
    (let [state (sync-state db req)]
      (if comparison
        (or (= state comparison)
            (isa? state comparison))
        state))))

;; Used to find, e.g. all requests like [:get :topic] or [:post :host]
(rf/reg-sub ::sync-state-q
  (fn [db [_ query]]
    (medley/filter-keys (partial stcu/projection? query) (::reqs db))))

(sth/rr rf/reg-event-db ::default-sync-success
  [rf/trim-v]
  (fn [db [{{:keys [response-data]} :resp
            :keys [req]}]]
    (let [response-data (if (nil? response-data) [] response-data)]
      (if (vector? response-data)
        (stcf/update-db db response-data)
        (do (log/warn "Sync response data was not a vector:" {:response-data response-data
                                                              :req           (into [] (take 2 req))})
            db)))))

(sth/rr rf/reg-event-fx ::default-sync-fail
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [{:keys [req], {:keys [response-data]} :resp}]]
    (let [sync-info {:response-data response-data :req (into [] (take 2 req))}]
      (when-not (vector? response-data)
        (log/warn "Sync response data was not a vector:" sync-info))
      (cond-> {:dispatch [::stfaf/add-failure [:sync sync-info]]}
        (vector? response-data) (assoc :db (stcf/update-db db response-data))))))

(sth/rr rf/reg-event-fx ::default-sync-unavailable
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [{:keys [req]}]]
    (let [sync-info {:req (into [] (take 2 req))}]
      (log/warn "Service unavailable. Try `(dev) (go)` in your REPL." sync-info)
      {:dispatch [::stfaf/add-failure [:sync sync-info]]})))

;;-----------------------
;; dispatch sync requests
;;-----------------------

(def default-handlers
  {:default-on {:success           ^:displace [[::default-sync-success :$ctx]]
                :fail              ^:displace [[::default-sync-fail :$ctx]]
                ::anom/unavailable ^:displace [[::default-sync-unavailable :$ctx]]}})

;;---
;; helpers

(defn add-default-sync-response-handlers
  [req]
  (update req 2 #(meta-merge default-handlers
                             {:$ctx {:req req}} %)))

(defn reconcile-default-handlers
  "Adds default handlers"
  [req]
  (let [{:keys [default-on on] :as opts} (get req 2)]
    (assoc req 2 (reduce-kv (fn [opts handler-name default-handler-events]
                              (assoc-in opts [:on handler-name] (let [on-events (handler-name on)]
                                                                  (if (vector? default-handler-events)
                                                                    (stcc/compose-events default-handler-events on-events)
                                                                    on-events))))
                            opts
                            default-on))))

(defn unsugar-handlers
  [req]
  (update req 2 stcu/move-keys #{:fail :success} [:on]))


(defn adapt-req
  "Makes sure a path is findable from req and adds it"
  [[method route-name opts :as _res] router]
  (when-let [path (strp/path router
                             route-name
                             (or (:route-params opts)
                                 (:params opts)
                                 opts)
                             (:query-params opts))]
    [method route-name (assoc opts :path path)]))

(defn ctx-db
  "db coeffect in interceptor"
  [ctx]
  (get-in ctx [:coeffects :db]))

(defn ctx-req
  "Retrieve request within interceptor"
  [ctx]
  (get-in ctx [:coeffects :event 1]))

(defn update-ctx-req-opts
  [ctx f]
  (update-in ctx [:coeffects :event 1 2] f))

(defn sync-rule?
  [ctx rule]
  (contains? (get-in (ctx-req ctx) [2 :rules]) rule))

(defn ctx-sync-state
  [ctx]
  (sync-state (ctx-db ctx) (ctx-req ctx)))

;;---
;; sync interceptors
;;---
(defn sync-entity-req
  "To be used when dispatching a sync event for an entity:
  (sync-entity-req :put :comment {:id 1 :content \"comment\"})"
  [[method route ent & [opts]]]
  [method route (-> opts
                    (update :params #(or % ent))
                    (update :route-params #(or % ent)))])

(def sync-once
  {:id     ::sync-once
   :before (fn [ctx]
             (if (and (sync-rule? ctx :once)
                      (= :success (ctx-sync-state ctx)))
               {:queue []}
               ctx))
   :after  identity})

(def sync-when-not-active
  {:id     ::sync-when-not-active
   :before (fn [ctx]
             (if (and (sync-rule? ctx :when-not-active)
                      (= :active (ctx-sync-state ctx)))
               {:queue []}
               ctx))
   :after  identity})

(def sync-route-params
  "Merges frontend route params into API request's route-params"
  {:id     ::sync-route-params
   :before (fn [ctx]
             (if (sync-rule? ctx :merge-route-params)
               (update-ctx-req-opts ctx (fn [opts]
                                          (merge {:route-params (paths/get-path (ctx-db ctx) :nav [:route :params])}
                                                 opts)))
               ctx))
   :after  identity})


;;---
;; populate sync with path data

(defn merge-ent-params
  [opts ent]
  (merge {:route-params ent, :params ent}
         opts))

(defn get-in-path
  [ctx path-kw path-fn]
  (if-let [path (get-in (ctx-req ctx) [2 path-kw])]
    (if-let [ent (path-fn path)]
      (update-ctx-req-opts ctx #(merge-ent-params % ent))
      (log/warn ::sync-entity-ent-not-found {path-kw path}))
    ctx))

;; Use the entity at given path to populate route-params and params of request
(def sync-entity-path
  {:id     ::sync-entity-path
   :before (fn [ctx]
             (get-in-path ctx :entity-path #(paths/get-path (ctx-db ctx) :entity %)))
   :after  identity})

;; Use the form buffer at given path to populate route-params and params of request
(def sync-form-path
  {:id     ::sync-form-path
   :before (fn [ctx]
             (get-in-path ctx :form-path #(paths/get-path (ctx-db ctx) :form % :buffer)))
   :after  identity})

(def sync-data-path
  {:id     ::sync-data-path
   :before (fn [ctx]
             (get-in-path ctx :data-path #(get-in (ctx-db ctx) %)))
   :after  identity})
;; end populate sync with path data

(def sync-methods
  {"get"    :get
   "put"    :put
   "delete" :delete
   "post"   :post
   "patch"  :patch})

(def sync-method
  {:id     ::sync-method
   :before (fn [ctx]
             (if-let [method (get sync-methods (name (get-in ctx [:coeffects :event 0])))]
               (update-in ctx [:coeffects :event] (fn [[event-name & args]]
                                                    (conj [event-name] (into [method] args))))
               ctx))
   :after  identity})

(def sync-interceptors
  [sync-method
   sync-route-params
   sync-entity-path
   sync-form-path
   sync-data-path
   sync-once
   sync-when-not-active
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
  [{:keys [db] :as _cofx} req]
  (let [{:keys [router sync-dispatch-fn]} (paths/get-path db :system ::sync)
        adapted-req                       (-> req
                                              (add-default-sync-response-handlers)
                                              (unsugar-handlers)
                                              (reconcile-default-handlers)
                                              (adapt-req router))]
    (if adapted-req
      {:db             (track-new-request db adapted-req)
       ::dispatch-sync {:dispatch-fn sync-dispatch-fn
                        :req         adapted-req}}
      (do (log/warn "sync router could not match req"
                    {:req (update req 2 select-keys [:params :route-params :query-params :data])})
          {:db db}))))

(s/fdef sync-event-fx
  :args (s/cat :cofx ::ss/cofx :req ::req)
  :ret  (s/keys :req-un [::ss/db]
                :req    [::dispatch-sync]))

;;---
;; handlers

;; The core event handler for syncing
(sth/rr rf/reg-event-fx ::sync
  sync-interceptors
  (fn [cofx [req]]
    (sync-event-fx cofx req)))

;; makes it a little easier to sync a single entity
(sth/rr rf/reg-event-fx ::sync-entity
  sync-interceptors
  (fn [cofx [req]]
    (sync-event-fx cofx (sync-entity-req req))))

;; Like `::sync`, but only fires if there hasn't previously been a
;; successful request with the same signature
(sth/rr rf/reg-event-fx ::sync-once
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :success (sync-state (:db cofx) req))
      (sync-event-fx cofx req))))

;; makes it a little easier to sync a single entity once
(sth/rr rf/reg-event-fx ::sync-entity-once
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :success (sync-state (:db cofx) req))
      (sync-event-fx cofx (sync-entity-req req)))))

;; only sync if there's no active sync
(sth/rr rf/reg-event-fx ::sync-unless-active
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :active (sync-state (:db cofx) req))
      (sync-event-fx cofx req))))

;; only sync entity if there's no active sync
(sth/rr rf/reg-event-fx ::sync-entity-unless-active
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :active (sync-state (:db cofx) req))
      (sync-event-fx cofx (sync-entity-req req)))))

;; The effect handler that actually performs a sync
(sth/rr rf/reg-fx ::dispatch-sync
  (fn [{:keys [dispatch-fn req]}]
    (dispatch-fn req)))

;;---------------
;; sync responses
;;---------------
;; Unwraps response-data for update-db
(sth/rr rf/reg-event-db ::update-db
  [rf/trim-v]
  (fn [db [{{:keys [response-data]} :resp}]]
    (stcf/update-db db response-data)))

(sth/rr rf/reg-event-db ::replace-entities
  [rf/trim-v]
  (fn [db [{{:keys [response-data]} :resp}]]
    (stcf/replace-entities db response-data)))

;;------
;; event helpers
;;------

(defn build-opts
  [opts call-opts params]
  (let [{:keys [route-params params] :as new-opts} (-> (meta-merge opts call-opts)
                                                       (update :params meta-merge params)
                                                       (stcu/move-keys #{:success :fail} [:on]))]
    (cond-> new-opts
      (not route-params) (assoc :route-params params))))

(defn sync-req->sync-event
  [[method endpoint opts] & [call-opts params]]
  [::sync [method endpoint (build-opts opts call-opts params)]])

(defn sync-req->dispatch
  [req & [call-opts params]]
  {:dispatch (sync-req->sync-event req call-opts params)})

(defn sync-fx-handler
  "Returns an effect handler that dispatches a sync event"
  [req]
  (fn [_cofx [call-opts params]]
    {:dispatch (sync-req->sync-event req call-opts params)}))

(defmethod ig/init-key ::sync
  [_ opts]
  opts)

;;------
;; db patch exception
;;------
(defn db-patch-handle-exception
  [db ex-data]
  #?(:cljs (js/console.warn "sync exception" ex-data))
  db)

;;---------------
;; common sync
;;---------------
(doseq [method [::get ::put ::post ::delete ::patch]]
  (sth/rr rf/reg-event-fx method
    sync-interceptors
    (fn [cofx [req]]
      (sync-event-fx cofx req))))

;;---------------
;; response helpers
;;---------------

(defn single-entity
  [ctx]
  (->> (get-in ctx [:resp :response-data])
       (filter #(= :entity (first %)))
       (first)
       (second)
       (vals)
       (first)
       (vals)
       (first)))

;;---------------
;; sync subs
;;---------------
(defn sync-subs
  [req-id]
  {:sync-state    (rf/subscribe [::sync-state req-id])
   :sync-active?  (rf/subscribe [::sync-state req-id :active])
   :sync-success? (rf/subscribe [::sync-state req-id :success])
   :sync-fail?    (rf/subscribe [::sync-state req-id :fail])})


;;---------------
;; sync req data handlers
;;---------------
(defn remove-reqs
  [db key-filter value-filter]
  (let [key-filter   (or key-filter (constantly false))
        value-filter (or value-filter (constantly false))]
    (update db (paths/prefix :reqs)
            (fn [req-map]
              (->> req-map
                   (remove (fn [[k v]] (and (key-filter k) (value-filter v))))
                   (into {}))))))

(sth/rr rf/reg-event-db ::remove-reqs
  [rf/trim-v]
  (fn [db [key-filter value-filter]]
    (remove-reqs db key-filter value-filter)))

;; remove all sync reqs dispatched while `route` was active
;; with a method found in set `methods`
(sth/rr rf/reg-event-db ::remove-reqs-by-route-and-method
  [rf/trim-v]
  (fn [db [route methods]]
    (remove-reqs db
                 #(methods (first %))
                 #(= (:route-name route)
                     (get-in % [:active-route :route-name])))))
