(ns donut.frontend.form.flow
  (:require
   [clojure.set :as set]
   [donut.frontend.handlers :as dh]
   [donut.frontend.path :as p]
   [donut.frontend.sync.flow :as dsf]
   [donut.sugar.utils :as dsu]
   [meta-merge.core :refer [meta-merge]]
   [re-frame.core :as rf]
   [re-frame.loggers :as rfl]))

;;------
;; helpers
;;------
(defn assoc-in-form
  [db partial-form-path ks v]
  (assoc-in db (p/path :form (into partial-form-path ks)) v))

(defn update-in-form
  [db partial-form-path ks f & args]
  (apply update-in db (p/path :form (into partial-form-path ks)) f args))

;;------
;; Form subs
;;------

(rf/reg-sub ::form
  (fn [db [_ partial-form-path]]
    (p/get-path db :form partial-form-path)))

(defn form-signal
  [[_ partial-form-path]]
  (rf/subscribe [::form partial-form-path]))

(def sub-name->form-key
  {::state        :state
   ::ui-state     :ui-state
   ::errors       :errors
   ::buffer       :buffer
   ::base         :base
   ::input-events :input-events})

(def form-keys (set (vals sub-name->form-key)))

;; regsiter these subscriptions
(doseq [[sub-name attr] sub-name->form-key]
  (rf/reg-sub sub-name
    form-signal
    (fn [form _]
      (get form attr))))

(rf/reg-sub ::state-success?
  form-signal
  (fn [form _]
    (= :success (:state form))))

;; Value for a specific form attribute
(defn attr-facet-sub
  [facet]
  (fn [[_ partial-form-path]]
    (rf/subscribe [facet partial-form-path])))

(rf/reg-sub ::attr-buffer
  (attr-facet-sub ::buffer)
  (fn [buffer [_ _partial-form-path attr-path]]
    (get-in buffer (dsu/vectorize attr-path))))

(rf/reg-sub ::attr-errors
  (attr-facet-sub ::errors)
  (fn [errors [_ _partial-form-path attr-path]]
    (get-in errors (dsu/vectorize attr-path))))

(rf/reg-sub ::attr-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events [_ _partial-form-path attr-path]]
    (get-in input-events (dsu/vectorize attr-path))))

;; Has the user interacted with the input that corresponds to this
;; attr?
(rf/reg-sub ::form-dirty?
  (fn [[_ partial-form-path]]
    [(rf/subscribe [::base partial-form-path])
     (rf/subscribe [::buffer partial-form-path])])
  (fn [[base data]]
    (not= base data)))

;; sync states
(defn sync-state
  [db [_ [method form-handle entity]]]
  (dsf/sync-state db [method form-handle {:route-params entity}]))

(rf/reg-sub ::sync-state sync-state)

(rf/reg-sub ::sync-active?
  (fn [db args]
    (= (sync-state db args) :active)))

(rf/reg-sub ::sync-success?
  (fn [db args]
    (= (sync-state db args) :success)))

(rf/reg-sub ::sync-fail?
  (fn [db args]
    (= (sync-state db args) :fail)))

;;------
;; Errors
;;------

;; TODO get rid of this
;; returns attr errors only when the form or given input has received
;; one of the input events in `show-errors-on`
(rf/reg-sub ::attr-visible-errors
  (fn [[_ & args]]
    [(rf/subscribe (into [::attr-input-events] args))
     (rf/subscribe (into [::input-events] args))
     (rf/subscribe (into [::attr-errors] args))])
  (fn [[attr-input-events input-events attr-errors] [_ _ _ show-errors-on]]
    (when (not-empty (set/intersection show-errors-on (set (into attr-input-events (::form input-events)))))
      attr-errors)))

;;------
;; Interacting with forms
;;------

(defn input-event
  "Meant to handle all input events: focus, blur, change, etc"
  [db [{:keys [partial-form-path attr-path val event-type] :as opts}]]
  (update-in db
             (p/path :form partial-form-path)
             (fn [form]
               (cond-> form
                 event-type            (update-in (dsu/flatv :input-events attr-path) (fnil conj #{}) event-type)
                 (contains? opts :val) (assoc-in (dsu/flatv :buffer attr-path) val)))))

(dh/rr rf/reg-event-db ::input-event
  [rf/trim-v]
  input-event)

(defn form-input-event
  "conj an event-type onto the form's `:input-events`"
  [db [{:keys [partial-form-path event-type]}]]
  (update-in db
             (p/path :form (into partial-form-path [:input-events ::form]))
             (fnil conj #{})
             event-type))

(dh/rr rf/reg-event-db ::form-input-event
  [rf/trim-v]
  form-input-event)

(defn attr-input-event
  "conj an event-type onto the attr's `:input-events`"
  [db [{:keys [partial-form-path attr-path event-type]}]]
  (update-in db
             (p/path :form (into partial-form-path [:input-events attr-path]))
             (fnil conj #{})
             event-type))

(dh/rr rf/reg-event-db ::attr-input-event
  [rf/trim-v]
  attr-input-event)

;;------
;; Building and submitting forms
;;------

(defn reset-form-buffer
  "Reset buffer to value when form was initialized. Typically paired with a 'reset' button"
  [db [partial-form-path]]
  (update-in db (p/path :form partial-form-path) (fn [{:keys [base] :as form}]
                                                   (assoc form :buffer base))))

(dh/rr rf/reg-event-db ::reset-form-buffer
  [rf/trim-v]
  reset-form-buffer)

(defn initialize-form
  [db [partial-form-path {:keys [buffer] :as form}]]
  (assoc-in db
            (p/path :form partial-form-path)
            (update form :base #(or % buffer))))

;; Populate form initial state
(dh/rr rf/reg-event-db ::initialize-form
  [rf/trim-v]
  initialize-form)

(defn initialize-form-from-path
  [db [partial-form-path {:keys [data-path data-fn]
                          :or   {data-fn identity}
                          :as   form}]]
  (initialize-form db [partial-form-path (-> form
                                             (assoc :buffer (data-fn (get-in db (dsu/vectorize data-path))))
                                             (dissoc :data-path :data-fn))]))

;; Populate form initial state
(dh/rr rf/reg-event-db ::initialize-form-from-path
  [rf/trim-v]
  initialize-form-from-path)

(defn set-form
  [db [partial-form-path form]]
  (assoc-in db (p/path :form partial-form-path) form))

(dh/rr rf/reg-event-db ::set-form
  [rf/trim-v]
  set-form)

(defn clear-form
  [db args]
  (set-form db (take 1 args)))

(dh/rr rf/reg-event-db ::clear-form
  [rf/trim-v]
  clear-form)

(defn clear-selected-keys
  [db partial-form-path clear]
  (update-in db
             (p/path :form partial-form-path)
             select-keys
             (if (or (= :all clear) (nil? clear))
               #{}
               (set/difference form-keys (set clear)))))

(dh/rr rf/reg-event-db ::clear
  [rf/trim-v]
  (fn [db [partial-form-path clear]]
    (prn "clearing" partial-form-path)
    (clear-selected-keys db partial-form-path clear)))

(dh/rr rf/reg-event-db ::keep
  [rf/trim-v]
  (fn [db [partial-form-path keep-keys]]
    (update-in db (p/path :form partial-form-path) select-keys keep-keys)))

(dh/rr rf/reg-event-db ::replace-with-response
  [rf/trim-v]
  (fn [db [{:keys [partial-form-path] :as ctx}]]
    (let [data  (dsf/single-entity ctx)]
      (-> db
          (assoc-in (p/path :form partial-form-path :base) data)
          (assoc-in (p/path :form partial-form-path :buffer) data)))))

;; TODO spec set of possible actions
;; TODO spec out form map, keys :buffer :state :ui-state etc
(def form-states #{nil :submitting :success :sleeping})

(defn form-sync-opts
  "Returns a request that the sync handler can use

  `form-handle`, the first element in a partial form path, is usually
  the route name. however, say you want to display two
  `[:todos :post]` forms. You don't want them to store their form
  data in the same place, so for one you use the partial form path
  `[:todos-a :post]` and for the other you use `[:todos-b :post]`.

  You would then need to include `:route-name` in the `sync` opts.

  - `success` and `fail` are the handlers for request completion.
  - `form-spec` is a way to pass on whatevs data to the request
    completion handler.
  - the `:sync` key of form spec can customize the sync request"
  [[method form-handle route-params :as partial-form-path], data, {:keys [sync] :as form-spec}]
  (let [route-name (get sync :route-name form-handle)
        method     (get sync :method method)

        sync-opts (meta-merge {:default-on   {:success [[::submit-form-success :$ctx]
                                                        [::dsf/default-sync-success :$ctx]]
                                              :fail    [[::submit-form-fail :$ctx]]}
                               :$ctx         {:full-form-path    (p/path :form partial-form-path)
                                              :partial-form-path partial-form-path
                                              :form-spec         form-spec}
                               :params       data
                               :route-params (or route-params data)
                               :rules        #{:when-not-active}}
                              sync)
        ;; custom req-key to handle the fact that form-handle can be
        ;; different from the route name
        sync-opts (update sync-opts
                          ::dsf/req-key
                          #(or % (dsf/req-key [method form-handle sync-opts])))]
    [method route-name sync-opts]))

(defn submit-form
  "build form request. update db to indicate form's submitting, clear
  old errors"
  [{:keys [db]} [partial-form-path & [form-spec]]]
  (let [full-form-path (p/path :form partial-form-path)]
    {:db       (-> db
                   (update-in full-form-path merge {:state :submitting, :errors nil})
                   (update-in (into full-form-path [:input-events ::form]) (fnil conj #{}) "submit"))
     :dispatch [::dsf/sync (form-sync-opts partial-form-path
                                           (merge (:data form-spec)
                                                  (get-in db (conj full-form-path :buffer)))
                                           form-spec)]}))

(dh/rr rf/reg-event-fx ::submit-form
  [rf/trim-v]
  submit-form)

;; when user clicks submit on form that has errors
(dh/rr rf/reg-event-db ::register-form-submit
  [rf/trim-v]
  (fn [db [partial-form-path]]
    (update-in db
               (p/path :form (into partial-form-path [:input-events ::form]))
               (fnil conj #{}) "submit")))

;;--------------------
;; deleting
;;--------------------

;; TODO handle id-key in a universal manner
(defn delete-entity-optimistic-fn
  "Returns a handler that can be used to both send a delete sync and
  remove the entity from the ent db"
  [ent-type & [id-key]]
  (let [id-key (or id-key :id)]
    (fn [{:keys [db] :as cofx} [entity :as args]]
      (merge ((dsf/sync-fx-handler [:delete ent-type]) cofx args)
             {:db (update-in db [:entity ent-type] dissoc (id-key entity))}))))

;;--------------------
;; handle form success/fail
;;--------------------

(dh/rr rf/reg-event-db ::submit-form-success
  [rf/trim-v]
  (fn [db [{:keys [full-form-path]}]]
    (assoc-in db (conj full-form-path :state) :success)))

(defn submit-form-fail
  [db [{:keys [full-form-path resp]
        {:keys [response-data]} :resp}]]
  (rfl/console :info "form submit fail:" resp full-form-path)
  (-> (assoc-in db (conj full-form-path :errors) (or (get-in response-data [0 1]) {:cause :unknown}))
      (assoc-in (conj full-form-path :state) :sleeping)))

(dh/rr rf/reg-event-db ::submit-form-fail
  [rf/trim-v]
  submit-form-fail)

;;--------------------
;; form ui
;;--------------------

(defn toggle-form
  [db path data]
  (update-in db (p/path :form path)
             (fn [form]
               (let [{:keys [ui-state]} form]
                 (if ui-state
                   nil
                   {:buffer data
                    :base data
                    :ui-state true})))))

(rf/reg-event-db ::toggle-form
  [rf/trim-v]
  (fn [db [path data]] (toggle-form db path data)))
