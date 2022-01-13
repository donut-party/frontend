(ns donut.frontend.sync.flow
  "Sync provides tools for syncing data across nodes, and for tracking
  the state of sync requests.

  The term 'sync' is used instead of AJAX"
  (:require [re-frame.core :as rf]
            [re-frame.loggers :as rfl]
            [donut.frontend.handlers :as dh]
            [donut.frontend.path :as path]
            [donut.frontend.routes.protocol :as drp]
            [donut.sugar.utils :as dsu]
            [donut.frontend.routes :as dfr]
            [donut.failure.flow :as dfaf]
            [medley.core :as medley]
            [clojure.walk :as walk]
            [meta-merge.core :refer [meta-merge]]
            [reitit.core :as r]
            [taoensso.timbre :as log]
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
              [:params {:optional true} Params]
              [::req-id {:optional true} some?]])

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
(defn req-key
  "returns a 'normalized' req path for a request.

  normalized in the sense that when it comes to distinguishing requests in order
  to track them, some of the variations between requests are significant, and
  some aren't:
  [:get :foo {:id 1, :params {:content \"vader\"}}]
  probably shouldn't be distinguished from
  [:get :foo {:id 1, :params {:content \"chewbacca\"}}].

  The first two elements of the request, `method` and `route-name`, are always
  significant. Where things get tricky is with `opts`. We don't want to use
  `opts` itself because the variation would lead to \"identical\" requests being
  treated as separate.

  Therefore we use `dfr/req-id` to select a subset of opts to distinguish
  requests with the same `method` and `route-name`. Sync routes can specify a
  `:id-key`, a keyword like `:id` or `:db/id` that identifies an entity.
  `dfr/req-id` will use that value if present.

  It's also possible to completely specify the req-key with `::req-key`."
  [[method route opts]]
  (or (::req-key opts)
      (let [req-id (dfr/req-id route opts)]
        (if (empty? req-id)
          [method route]
          [method route req-id]))))

(defn track-new-request
  "Adds a request's state te the app-db and increments the active request
  count"
  [db req]
  (-> db
      (update-in (path/reqs [(req-key req)])
                 merge
                 {:state            :active
                  :active-nav-route (path/get-path db :nav :route)})
      (update ::active-request-count (fnil inc 0))))

(defn remove-req
  [db req]
  (update-in db [:donut :reqs] dissoc (req-key req)))

;;------
;; dispatch handler wrappers
;;------
(defn sync-finished
  "Update sync bookkeeping"
  [db [_ req resp]]
  (-> db
      (assoc-in (path/reqs [(req-key req) :state]) (:status resp))
      (update ::active-request-count dec)))

(dh/rr rf/reg-event-db ::sync-finished
  []
  sync-finished)

(dh/rr rf/reg-event-fx ::sync-response
  [rf/trim-v]
  (fn [_ [dispatches]]
    {:fx (map (fn [a-dispatch] [:dispatch a-dispatch]) dispatches)}))

(defn vectorize-dispatches
  [xs]
  (cond (nil? xs)             []
        (keyword? (first xs)) [xs]
        :else                 xs))

(defn response-dispatches
  "Combine default response dispatches with request-specific response dispatches"
  [req {:keys [status] :as _resp}]
  (let [{:keys [default-on on] :as _rdata} (get req 2)

        default-dispatches (->> (get default-on status (get default-on
                                                            :fail))
                                (vectorize-dispatches))
        dispatches         (->> (get on status (get on :fail))
                                (vectorize-dispatches))]
    (into default-dispatches dispatches)))

(defn sync-response-handler
  "Used by sync implementations (e.g. ajax) to create a response handler"
  [req]
  (fn [resp]
    (let [rdata (get req 2)
          $ctx                   (assoc (get rdata :$ctx {})
                                        :resp resp
                                        :req  req)]
      (rf/dispatch [::sync-response
                    [[::sync-finished req resp]
                     (->> (response-dispatches req resp)
                          (walk/postwalk (fn [x] (if (= x :$ctx) $ctx x))))]]))))

;;------
;; registrations
;;------
(defn sync-state
  [db req]
  (path/get-path db :reqs [(req-key req) :state]))

(rf/reg-sub ::req
  (fn [db [_ req]]
    (path/get-path db :reqs [(req-key req)])))

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
    (medley/filter-keys (partial dsu/projection? query) (get-in db [:donut :reqs]))))

(defmulti sync-success
  "Dispatches based on the type of the response-data. Maps and vectors are treated
  identically; they're considered to be either a singal instance or collection
  of entities. Those entities are placed in the entity-db, replacing whatever's
  there.

  SegmentResponses are handled separately."
  (fn [db {{:keys [response-data]} :resp}]
    (cond (map? response-data) :map
          (vector? response-data) :vector
          :else (type response-data))))

(defmethod sync-success :map
  [db {{:keys [response-data]} :resp :as response}]
  ;; it's easier to forward to a base case, bruv
  (sync-success db (assoc-in response [:resp :response-data] [response-data])))

;; Updates db by replacing each entity with return value
(defmethod sync-success :vector
  [db {{:keys [response-data]} :resp
       :keys                   [req]
       :as                     _response}]
  ;; TODO handle case of `:ent-type` or `:id-key` missing
  (let [endpoint-router     (path/get-path db :system [:routes :endpoint-router])
        endpoint-route-name (second req)
        endpoint-route      (r/match-by-name endpoint-router endpoint-route-name)
        ent-type            (get-in endpoint-route [:data :ent-type])
        id-key              (get-in endpoint-route [:data :id-key])]
    ;; TODO This replacement strategy could be seriously flawed! I'm trying to
    ;; keep this simple and make possibly problematic code obvious
    (reduce (fn [db ent]
              (assoc-in db (path/path :entity [ent-type (id-key ent)]) ent))
            db
            response-data)))

(defmethod sync-success nil [db _] db)

(defmethod sync-success :default
  [db {{:keys [response-data]} :resp
       :keys                   [req]
       :as                     _response}]

  (rfl/log :warn
           "sync response data type was not recognized"
           {:response-data response-data
            :req (into [] (take 2 req))})
  db)

(dh/rr rf/reg-event-db ::default-sync-success
  [rf/trim-v]
  (fn [db [response]] (sync-success db response)))

(dh/rr rf/reg-event-fx ::default-sync-fail
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [{:keys [req], {:keys [response-data]} :resp}]]
    ;; TODO possibly allow failed responses to carry data
    (let [sync-info {:response-data response-data :req (into [] (take 2 req))}]
      (rfl/log :info "sync failed" sync-info)
      {:dispatch [::dfaf/add-failure [:sync sync-info]]})))

(dh/rr rf/reg-event-fx ::default-sync-unavailable
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [{:keys [req]}]]
    (let [sync-info {:req (into [] (take 2 req))}]
      (rfl/log :warn "Service unavailable. Try `(dev) (go)` in your REPL." sync-info)
      {:dispatch [::dfaf/add-failure [:sync sync-info]]})))

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
  (update req 2 #(meta-merge default-handlers {:$ctx {:req req}} %)))

(defn adapt-req
  "Makes sure a path is findable from req and adds it"
  [[method route-name opts :as _res] router]
  (when-let [path (drp/path router
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

(defn ctx-sync-state
  [ctx]
  (sync-state (ctx-db ctx) (ctx-req ctx)))

;;---
;; sync interceptors
;;---

;; You can specify `:rules` in a sync request's options to modify its behavior,
;; e.g. by only syncing once or only syncing when not active.

(defn sync-rule?
  [ctx rule]
  (contains? (get-in (ctx-req ctx) [2 :rules]) rule))

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

(def sync-merge-route-params
  "Merges frontend route params into API request's route-params"
  {:id     ::sync-merge-route-params
   :before (fn [ctx]
             (if (sync-rule? ctx :merge-route-params)
               (update-ctx-req-opts ctx (fn [opts]
                                          (merge {:route-params (path/get-path (ctx-db ctx) :nav [:route :params])}
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
             (get-in-path ctx :entity-path #(path/get-path (ctx-db ctx) :entity %)))
   :after  identity})

;; Use the form buffer at given path to populate route-params and params of request
(def sync-form-path
  {:id     ::sync-form-path
   :before (fn [ctx]
             (get-in-path ctx :form-path #(path/get-path (ctx-db ctx) :form % :buffer)))
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
             ;; TODO figure out why this is needed
             (if-let [method (get sync-methods (name (get-in ctx [:coeffects :event 0])))]
               (update-in ctx
                          [:coeffects :event]
                          (fn [[event-name & args]]
                            (conj [event-name] (into [method] args))))
               ctx))
   :after  identity})

(def sync-interceptors
  [sync-method
   sync-merge-route-params
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
  (let [{:keys [router sync-dispatch-fn]} (path/get-path db :system ::sync)
        adapted-req                       (-> req
                                              (add-default-sync-response-handlers)
                                              (adapt-req router))]
    (if adapted-req
      {:db             (track-new-request db adapted-req)
       ::dispatch-sync {:dispatch-fn sync-dispatch-fn
                        :req         adapted-req}}
      (do (log/warn "sync router could not match req"
                    {:req (update req 2 select-keys [:params :route-params :query-params :data])})
          {:db db}))))

;;---
;; handlers

;; The core event handler for syncing
(dh/rr rf/reg-event-fx ::sync
  sync-interceptors
  (fn [cofx [req]]
    (sync-event-fx cofx req)))

;; makes it a little easier to sync a single entity
(dh/rr rf/reg-event-fx ::sync-entity
  sync-interceptors
  (fn [cofx [req]]
    (sync-event-fx cofx (sync-entity-req req))))

;; The effect handler that actually performs a sync
(dh/rr rf/reg-fx ::dispatch-sync
  (fn [{:keys [dispatch-fn req]}]
    (dispatch-fn req)))

;;------
;; event helpers
;;------

(defn build-opts
  [opts call-opts params]
  (let [{:keys [route-params params] :as new-opts} (-> opts
                                                       (meta-merge call-opts)
                                                       (update :params meta-merge params))]
    ;; by default also use param for route params
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

;;---------------
;; common sync
;;---------------

(doseq [method [::get ::put ::post ::delete ::patch]]
  (dh/rr rf/reg-event-fx method
    sync-interceptors
    (fn [cofx [req]]
      (sync-event-fx cofx req))))

;;---------------
;; response helpers
;;---------------

(defn single-entity
  [ctx]
  (get-in ctx [:resp :response-data]))

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
    (update db (path/path :reqs [])
            (fn [req-map]
              (->> req-map
                   (remove (fn [[k v]] (and (key-filter k) (value-filter v))))
                   (into {}))))))

(dh/rr rf/reg-event-db ::remove-reqs
  [rf/trim-v]
  (fn [db [key-filter value-filter]]
    (remove-reqs db key-filter value-filter)))

;; remove all sync reqs dispatched while `route` was active
;; with a method found in set `methods`
(dh/rr rf/reg-event-db ::remove-reqs-by-route-and-method
  [rf/trim-v]
  (fn [db [route methods]]
    (remove-reqs db
                 #(methods (first %))
                 #(= (:route-name route)
                     (get-in % [:active-route :route-name])))))
