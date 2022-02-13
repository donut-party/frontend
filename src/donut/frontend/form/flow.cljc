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


;;--------------------
;; specs
;;--------------------

;;---
;; form buffer
;;---

(def BufferViewAttrKey [:vector any?])
(def BufferViewMap [:map-of BufferViewAttrKey any?])
(def BufferView [:map
                 [:attrs {:optional true} BufferViewMap]
                 [:form {:optional true} any?]])

(def FormBuffer [:map])
(def FormErrors BufferViewMap)
(def FormInputEvents BufferViewMap)
(def FormBufferInitVal [:map])
(def FormUIState any?)

(def Form
  [:map
   [:buffer FormBuffer]
   [:errors {:optional true} FormErrors]
   [:input-events {:optional true} FormInputEvents]
   [:buffer-init-val FormBufferInitVal]
   [:ui-state {:optional true} FormUIState]])


;;---
;; form submitting options
;;---

(def FormSubmitOpts
  [:map
   [:sync]
   [:data]])

;;---
;; form config
;;---

(def FormLayout
  [:map
   [:donut.form/key :any]
   [:donut.form.layout/buffer {:optional true} [:vector keyword?]]
   [:donut.form.layout/errors {:optional true} [:vector keyword?]]
   [:donut.form.layout/input-events {:optional true} [:vector keyword?]]
   [:donut.form.layout/buffer-init-val {:optional true} [:vector keyword?]]
   [:donut.form.layout/ui-state {:optional true} [:vector keyword?]]])

(def form-layout-keys (set (map first (rest FormLayout))))

;;--------------------
;; Form subs
;;--------------------

(rf/reg-sub ::form
  (fn [db [_ form-key]]
    (p/get-path db :form [form-key])))

(defn form-signal
  [[_ form-key]]
  (rf/subscribe [::form form-key]))

(def sub-name->inner-key
  {::buffer          :buffer
   ::errors          :errors
   ::input-events    :input-events
   ::buffer-init-val :buffer-init-val
   ::ui-state        :ui-state})

(def inner-keys (set (vals sub-name->inner-key)))

;; register these subscriptions
(doseq [[sub-name inner-key] sub-name->inner-key]
  (rf/reg-sub sub-name
    form-signal
    (fn [form _]
      (get form inner-key))))

;; Value for a specific form attribute
(defn attr-facet-sub
  [facet]
  (fn [[_ form-key]]
    (rf/subscribe [facet form-key])))

(rf/reg-sub ::attr-buffer
  (attr-facet-sub ::buffer)
  (fn [buffer [_ _form-key attr-path]]
    (get-in buffer (dsu/vectorize attr-path))))

(rf/reg-sub ::attr-errors
  (attr-facet-sub ::errors)
  (fn [errors [_ _form-key attr-path]]
    (get-in errors (into [:attrs] (dsu/vectorize attr-path)))))

(rf/reg-sub ::form-errors
  (attr-facet-sub ::errors)
  (fn [errors _]
    (:form errors)))

(rf/reg-sub ::attr-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events [_ _form-key attr-path]]
    (get-in input-events (into [:attrs] (dsu/vectorize attr-path)))))

(rf/reg-sub ::form-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events _]
    (:form input-events)))

;; Has the user modified the buffer?
(rf/reg-sub ::form-dirty?
  (fn [[_ form-key]]
    [(rf/subscribe [::buffer-init-val form-key])
     (rf/subscribe [::buffer form-key])])
  (fn [[buffer-init-val buffer]]
    (not= buffer-init-val buffer)))

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
     (rf/subscribe (into [::form-input-events] args))
     (rf/subscribe (into [::attr-errors] args))])
  (fn [[attr-input-events form-input-events attr-errors] [_ _ _ show-errors-on]]
    (when (->> form-input-events
               (into attr-input-events)
               set
               (set/intersection show-errors-on)
               not-empty)
      attr-errors)))

;;------
;; Interacting with forms
;;------

(defn attr-input-event
  "Meant to handle all input events: focus, blur, change, etc"
  [db [{:donut.form.layout/keys [buffer input-events]
        :keys                   [form-key attr-path val event-type]
        :as                     opts}]]
  (cond-> db
    event-type            (update-in (conj input-events attr-path)
                                     (fnil conj #{})
                                     event-type)
    (contains? opts :val) (assoc-in (into buffer (dsu/vectorize attr-path))
                                    val)))

(dh/rr rf/reg-event-db ::attr-input-event
  [rf/trim-v]
  attr-input-event)

(defn form-input-event
  "conj an event-type onto the form's `:input-events`"
  [db [{:keys [form-key event-type]}]]
  (update-in db
             (p/path :form (into [form-key] [:input-events :form]))
             (fnil conj #{})
             event-type))

(dh/rr rf/reg-event-db ::form-input-event
  [rf/trim-v]
  form-input-event)

;;---------------------
;; Setting form data
;;---------------------

(defn reset-form-buffer
  "Reset buffer to value when form was initialized. Typically paired with a 'reset' button"
  [db [form-key]]
  (update-in db
             (p/form-path [form-key])
             (fn [{:keys [buffer-init-val] :as form}]
               (assoc form :buffer buffer-init-val))))

(dh/rr rf/reg-event-db ::reset-form-buffer
  [rf/trim-v]
  reset-form-buffer)

(defn initialize-form
  [db [form-key {:keys [buffer] :as form}]]
  (assoc-in db
            (p/form-path [form-key])
            (update form :buffer-init-val #(or % buffer))))

;; Populate form initial state
(dh/rr rf/reg-event-db ::initialize-form
  [rf/trim-v]
  initialize-form)

(defn initialize-form-from-path
  [db [form-key {:keys [data-path data-fn]
                          :or   {data-fn identity}
                          :as   form}]]
  (initialize-form db [form-key (-> form
                                             (assoc :buffer (data-fn (get-in db (dsu/vectorize data-path))))
                                             (dissoc :data-path :data-fn))]))

;; Populate form initial state
(dh/rr rf/reg-event-db ::initialize-form-from-path
  [rf/trim-v]
  initialize-form-from-path)

(defn set-form
  [db [form-key form]]
  (assoc-in db (p/form-path [form-key]) form))

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
  [db form-key clear]
  (update-in db
             (p/form-path [form-key])
             select-keys
             (if (or (= :all clear) (nil? clear))
               #{}
               (set/difference inner-keys (set clear)))))

(dh/rr rf/reg-event-db ::clear
  [rf/trim-v]
  (fn [db [form-key clear]]
    (clear-selected-keys db form-key clear)))

(dh/rr rf/reg-event-db ::keep
  [rf/trim-v]
  (fn [db [form-key keep-keys]]
    (update-in db (p/form-path [form-key]) select-keys keep-keys)))

(dh/rr rf/reg-event-db ::replace-with-response
  [rf/trim-v]
  (fn [db [{:keys [form-key] :as ctx}]]
    (let [data  (dsf/single-entity ctx)]
      (-> db
          (assoc-in (p/form-path [form-key :buffer-init-val]) data)
          (assoc-in (p/form-path [form-key :buffer]) data)))))

;;---------------------
;; submitting a form
;;---------------------

;; TODO spec set of possible actions

(defn form-sync-opts
  "Returns a request that the sync handler can use

  `form-handle`, the second element in a partial form path, is usually the route
  name. however, say you want to display two `[:post :todos]` forms. You don't
  want them to store their form data in the same place, so for one you use the
  partial form path `[:post :todos-a]` and for the other you use
  `[:post :todos-b]`.

  You would then need to include `:route-name` in the `sync` opts.

  - `success` and `fail` are the handlers for request completion.
  - `form-spec` is a way to pass on whatevs data to the request completion
    handler.
  - the `:sync` key of form spec can customize the sync request"
  [[method form-handle route-params :as form-key]
   buffer-data
   sync-opts]
  (let [route-name (get sync-opts :sync-route-name form-handle)
        method     (get sync-opts :method method)
        params     (merge (:params sync-opts) buffer-data)

        sync-opts (meta-merge {:default-on   {:success [[::submit-form-success :$ctx]
                                                        [::dsf/default-sync-success :$ctx]]
                                              :fail    [[::submit-form-fail :$ctx]]}
                               :$ctx         {:full-form-path (p/form-path [form-key])
                                              :form-key       form-key}
                               :params       params
                               :route-params (or route-params params)
                               ;; by default don't allow a form to be submitted
                               ;; when we're waiting for a response
                               :rules        #{:when-not-active}}
                              sync-opts)]
    [method route-name sync-opts]))

(defn submit-form
  "build form request. update db to indicate form's submitting, clear
  old errors"
  [{:keys [db]} [form-key & [form-spec]]]
  (let [full-form-path (p/form-path [form-key])]
    {:db       (-> db
                   (update-in full-form-path merge {:errors nil})
                   (update-in (into full-form-path [:input-events :form])
                              (fnil conj #{})
                              :submit))
     :dispatch [::dsf/sync (form-sync-opts form-key
                                           (get-in db (conj full-form-path :buffer))
                                           form-spec)]}))

(dh/rr rf/reg-event-fx ::submit-form
  [rf/trim-v]
  submit-form)

;; when user clicks submit on form that has errors
(dh/rr rf/reg-event-db ::register-form-submit
  [rf/trim-v]
  (fn [db [form-key]]
    (update-in db
               (p/form-path (into [form-key] [:input-events :form]))
               (fnil conj #{})
               :submit)))

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
    (assoc-in db (conj full-form-path :input-events) nil)))

(defn response-error
  [$ctx]
  ;; assumes response-data is something like [:errors {}]
  (get-in $ctx [:resp :response-data 0 1]))

(defn submit-form-fail
  [db [{:keys [full-form-path resp]
        :as $ctx}]]
  (rfl/console :log "form submit fail:" resp full-form-path)
  (-> db
      (assoc-in (conj full-form-path :errors)
                (or (response-error $ctx)
                    {:cause :unknown}))
      (assoc-in (conj full-form-path :input-events) nil)))

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
                   {:buffer          data
                    :buffer-init-val data
                    :ui-state        true})))))

(rf/reg-event-db ::toggle-form
  [rf/trim-v]
  (fn [db [path data]] (toggle-form db path data)))
