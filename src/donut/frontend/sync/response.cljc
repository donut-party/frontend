(ns donut.frontend.sync.response
  "This determines how to update the global state atom with data received from a
   sync request.

   NOTE this is one the most important parts of the system. As the primary
   interface between backend and frontend, it must be extensible and
   comprehensible.

   There are three points of extension:

   1. `response-data-types`. This maps a \"data type\" for the response to a
      predicate that gets applied to the response to see if the data type
      applies.

   2. `handle-sync-response-data`. This is a multimethod that dispatches on a
      response data type, determined by `response-data-types`

   3. `apply-sync-segment`. If response data contains a vector of vectors, the
      response data is treated as containing \"segments\". Segments are just
      2-vectors, with the first element identifying the _segment type_. This
      allows a response to contain mixed data. Each segment is handled by
      `apply-sync-segment`, a multimethod that dispatches on segment type.
  "
  (:require
   [donut.frontend.path :as p]
   [donut.frontend.routes.protocol :as drp]
   [re-frame.loggers :as rfl]))

(defn sync-data
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp
       :keys                   [:donut.frontend.sync.flow/req]}]
  (let [router     (p/get-path db :donut-component [:sync-router])
        route-name (:route-name req)
        route      (drp/route router route-name)]
    {:ent-type      (:ent-type route)
     :id-key        (:id-key route)
     :route-name    route-name
     :route-params  (:route-params req)
     :response-data response-data}))

(defn replace-ents
  [db ent-type id-key ents]
  (reduce (fn [db ent]
            (assoc-in db (p/path :entity [ent-type (id-key ent)]) ent))
          db
          ents))

(defn delete-ents
  [db ent-type id-key ents]
  (reduce (fn [db ent]
            (update-in db (p/path :entity [ent-type]) dissoc (id-key ent)))
          db
          ents))

(defn handle-sync-response-data-dispatch-fn
  [response-data]
  (let [v? (vector? response-data)
        e1 (when v? (first response-data))]
    (cond
      (map? response-data)     :entity
      (and v? (map? e1))       :entities
      (and v? (vector? e1))    :segments
      (empty? response-data)   :empty
      (and v? (keyword? e1))   :segment
      (keyword? response-data) response-data
      :else                    (throw (ex-info "could not determine response data type"
                                               {:response-data response-data})))))

(defmulti apply-sync-segment
  "Sync segments allow the backend to convey heterogenous data to the frontend.
  It's also the baseline customization point for response data, as segment
  dispatch happens on the first element of a vector, and that vector value can
  be anything! anything at all!"
  (fn [_db _sync-lifecycle [segment-type]] segment-type))

(defmethod apply-sync-segment :entities
  [db _sync-lifecycle [_ [ent-type id-key ents]]]
  (replace-ents db ent-type id-key ents))

(defmethod apply-sync-segment :delete-entities
  [db _sync-lifecycle [_ [ent-type id-key ents]]]
  (delete-ents db ent-type id-key ents))

(defmethod apply-sync-segment :auth
  [db _sync-lifecycle [_ auth-map]]
  (assoc-in db (p/path :auth) auth-map))

(defmethod apply-sync-segment :delete-routed-entity
  [db sync-lifeycle _]
  (let [{:keys [ent-type id-key route-params]} (sync-data db sync-lifeycle)]
    (delete-ents db ent-type id-key [route-params])))

(defmulti handle-sync-response-data
  "Dispatches based on the type of the response-data. Maps and vectors are treated
  identically; they're considered to be either a singal instance or collection
  of entities. Those entities are placed in the entity-db, replacing whatever's
  there. "
  (fn [_db {{:keys [response-data]} :donut.frontend.sync.flow/resp :as _sync}]
    (handle-sync-response-data-dispatch-fn response-data)))

(defn- forward-vector
  "response data can include either X or a [X]. this allows us to define X methods
  in terms of [X]"
  [db sync-lifecycle]
  (handle-sync-response-data
   db
   (update-in sync-lifecycle [:donut.frontend.sync.flow/resp :response-data] vector)))

;; Updates db by replacing each entity with return value
(defmethod handle-sync-response-data :entities
  [db sync]
  ;; TODO handle case of `:ent-type` or `:id-key` missing
  (let [{:keys [ent-type id-key route-name response-data]} (sync-data db sync)]
    ;; TODO This replacement strategy could be seriously flawed! I'm trying to
    ;; keep this simple and make possibly problematic code obvious
    (when-not id-key
      (throw (ex-info "could not determine id-key for sync response"
                      {:ent-type   ent-type
                       :route-name route-name})))
    (replace-ents db ent-type id-key response-data)))

(defmethod handle-sync-response-data :entity
  [db sync-lifecycle]
  (forward-vector db sync-lifecycle))

(defmethod handle-sync-response-data :segments
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp :as sync-lifecycle}]
  (reduce (fn [db segment] (apply-sync-segment db sync-lifecycle segment))
          db
          response-data))

(defmethod handle-sync-response-data :segment
  [db sync-lifecycle]
  (forward-vector db sync-lifecycle))

(defmethod handle-sync-response-data :empty [db _] db)

(defmethod handle-sync-response-data :default
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp
       :keys [:donut.frontend.sync.flow/req]}]
  (rfl/console :warn
               "sync response data type was not recognized"
               {:response-data response-data
                :req req})
  db)
