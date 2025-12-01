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


(defn replace-ents
  [db ent-type id-key ents]
  (reduce (fn [db ent]
            (assoc-in db (p/path :entity [ent-type (id-key ent)]) ent))
          db
          ents))

(def response-data-types
  "Response data types and predicates are broken out from sync-success like this
  to at least make it possible to alter them with set!"
  [[:entity   (fn [response-data]
                (map? response-data))]
   [:entities (fn [response-data]
                (and (vector? response-data)
                     (map? (first response-data))))]
   [:segments (fn [response-data]
                (and (vector? response-data)
                     (vector? (first response-data))))]
   [:empty    (fn [response-data]
                (empty? response-data))]])


(defmulti apply-sync-segment
  "Sync segments allow the backend to convey heterogenous data to the frontend."
  (fn [_db [segment-type]] segment-type))

(defmethod apply-sync-segment :entities
  [db [_ [ent-type id-key ents]]]
  (replace-ents db ent-type id-key ents))

(defmethod apply-sync-segment :auth
  [db [_ auth-map]]
  (assoc-in db (p/path :auth) auth-map))

(defmulti handle-sync-response-data
  "Dispatches based on the type of the response-data. Maps and vectors are treated
  identically; they're considered to be either a singal instance or collection
  of entities. Those entities are placed in the entity-db, replacing whatever's
  there. "
  (fn [_db {{:keys [response-data]} :donut.frontend.sync.flow/resp}]
    (loop [[[dt-name pred] & dts] response-data-types]
      (when (nil? dt-name)
        (throw (ex-info "could not determine response data type"
                        {:response-data response-data})))
      (if (pred response-data)
        dt-name
        (recur dts)))))

(defmethod handle-sync-response-data :entity
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp
       :as sync-lifecycle}]
  ;; it's easier to forward to a base case, bruv
  (handle-sync-response-data
   db
   (assoc-in sync-lifecycle [:donut.frontend.sync.flow/resp :response-data] [response-data])))

;; Updates db by replacing each entity with return value
(defmethod handle-sync-response-data :entities
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp
       :keys [:donut.frontend.sync.flow/req]}]
  ;; TODO handle case of `:ent-type` or `:id-key` missing
  (let [sync-router     (p/get-path db :donut-component [:sync-router])
        sync-route-name (:route-name req)
        sync-route      (drp/route sync-router sync-route-name)
        ent-type        (:ent-type sync-route)
        id-key          (:id-key sync-route)]
    ;; TODO This replacement strategy could be seriously flawed! I'm trying to
    ;; keep this simple and make possibly problematic code obvious
    (when-not id-key
      (throw (ex-info "could not determine id-key for sync response"
                      {:ent-type        ent-type
                       :sync-route-name sync-route-name})))
    (replace-ents db ent-type id-key response-data)))

(defmethod handle-sync-response-data :segments
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp}]
  (reduce apply-sync-segment db response-data))

(defmethod handle-sync-response-data :empty [db _] db)

(defmethod handle-sync-response-data :default
  [db {{:keys [response-data]} :donut.frontend.sync.flow/resp
       :keys [:donut.frontend.sync.flow/req]}]
  (rfl/console :warn
               "sync response data type was not recognized"
               {:response-data response-data
                :req req})
  db)
