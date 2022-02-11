(ns donut.frontend.pagination.flow
  (:require
   [re-frame.core :as rf]
   [donut.frontend.sync.flow :as dsf]
   [donut.frontend.path :as p]))

;;---------
;; Handlers
;;---------

;; TODO spec possible page states and page keys
;; TODO namespace the page key
(defn handle-page-segment
  [db segment]
  (update-in db (p/path :page) merge segment))

;;---------
;; Subscriptions
;;---------

(defn pager
  "Retrieve a query and its results"
  [db pager-id]
  (p/get-path db :page [pager-id]))

(rf/reg-sub ::pager (fn [db [_ pager-id]] (pager db pager-id)))

(rf/reg-sub ::page-data
  (fn [db [_ pager-id]]
    (let [{:keys [query result]} (pager db pager-id)]
      (map #(p/get-path db :entity [(:type query) %])
           (:ordered-ids (get result query))))))

(rf/reg-sub ::page-result
  (fn [db [_ pager-id]] (:result (pager db pager-id))))

(rf/reg-sub ::page-query
  (fn [db [_ pager-id]] (:query (pager db pager-id))))

(rf/reg-sub ::page-count
  (fn [db [_ pager-id]] (:page-count (pager db pager-id))))

(rf/reg-sub ::ent-count
  (fn [db [_ pager-id]] (:ent-count (pager db pager-id))))

(rf/reg-sub ::sync-state
  (fn [db [_ endpoint pager-id]]
    (dsf/sync-state db [:get endpoint {:sync-key pager-id}])))

;;---------
;; Helpers
;;---------

(defn update-db-page-loading
  "Use when initiating a GET request fetching paginated data"
  [db {:keys [pager-id] :as page-query}]
  (assoc-in db (p/page-path [pager-id :query]) page-query))

(defn page-sync-req
  [endpoint page-query & [opts]]
  [:get endpoint (merge {:query-params page-query
                         :sync-key     [:pagination (:pager-id page-query)]}
                        opts)])
