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

(def form-layout-keys (mapv first (rest FormLayout)))

(defn form-paths
  "By default all form data lives under [:donut :form form-key]. A form layout
  lets you specify different locations for form facets. This function translates
  your form layout into the actual paths to be used for subscriptions and
  events."
  [form-layout]
  (let [[form-key-key & layout-keys] form-layout-keys
        form-key                     (form-key-key form-layout)]
    (reduce (fn [m k]
              (assoc m k (or (k form-layout)
                             (p/form-path [form-key (keyword (name k))]))))
            {}
            layout-keys)))

;;--------------------
;; Form subs
;;--------------------

(rf/reg-sub ::form
  (fn [db [_ {:donut.form.layout/keys [buffer errors input-events buffer-init-val ui-state] :as form-layout}]]
    (if (or buffer errors input-events buffer-init-val ui-state)
      (let [{:donut.form.layout/keys [buffer errors input-events buffer-init-val ui-state]}
            (form-paths form-layout)]
        ;; TODO this could be a drag on performance. could do this more precisely.
        {:buffer          (get-in db buffer)
         :errors          (get-in db errors)
         :input-events    (get-in db input-events)
         :buffer-init-val (get-in db buffer-init-val)
         :ui-state        (get-in db ui-state)})
      (p/get-path db :form [(:donut.form/key form-layout)]))))

(defn form-signal
  [[_ form-layout]]
  (rf/subscribe [::form form-layout]))

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
  (fn [[_ form-layout]]
    (rf/subscribe [facet form-layout])))

(rf/reg-sub ::attr-buffer
  (attr-facet-sub ::buffer)
  (fn [buffer [_ _form-layout attr-path]]
    (prn "buffer" buffer)
    (get-in buffer (dsu/vectorize attr-path))))

(rf/reg-sub ::attr-errors
  (attr-facet-sub ::errors)
  (fn [errors [_ _form-layout attr-path]]
    (get-in errors (into [:attrs] (dsu/vectorize attr-path)))))

(rf/reg-sub ::form-errors
  (attr-facet-sub ::errors)
  (fn [errors _]
    (:form errors)))

(rf/reg-sub ::attr-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events [_ _form-layout attr-path]]
    (get-in input-events (into [:attrs] (dsu/vectorize attr-path)))))

(rf/reg-sub ::form-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events _]
    (:form input-events)))

;; Has the user modified the buffer?
(rf/reg-sub ::form-dirty?
  (fn [[_ form-layout]]
    [(rf/subscribe [::buffer-init-val form-layout])
     (rf/subscribe [::buffer form-layout])])
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
  [db [{:donut.input/keys [attr-path value event-type]
        :as               opts}]]
  (let [{:donut.form.layout/keys [buffer input-events]} (form-paths opts)]
    (cond-> db
      event-type                          (update-in (conj input-events attr-path)
                                                     (fnil conj #{})
                                                     event-type)
      (contains? opts :donut.input/value) (assoc-in (into buffer (dsu/vectorize attr-path))
                                                    value))))

(dh/rr rf/reg-event-db ::attr-input-event
  [rf/trim-v]
  attr-input-event)

;; TODO update this with form layout
(defn form-input-event
  "conj an event-type onto the form's `:input-events`"
  [db [{:keys [form-layout event-type]}]]
  (let [paths (form-paths form-layout)]
    (update-in db
               (:donut.form.layout/input-events paths)
               (fnil conj #{})
               event-type)))

(dh/rr rf/reg-event-db ::form-input-event
  [rf/trim-v]
  form-input-event)

;;---------------------
;; Setting form data
;;---------------------

(defn reset-form-buffer
  "Reset buffer to value when form was initialized. Typically paired with a 'reset' button"
  [db form-layout]
  (let [{:donut.form.layout/keys [buffer buffer-init-val]} (form-paths form-layout)]
    (assoc-in db buffer (get-in db buffer-init-val))))

(dh/rr rf/reg-event-db ::reset-form-buffer
  [rf/trim-v]
  reset-form-buffer)

(defn initialize-form
  [db form-layout {:keys [buffer] :as form}]
  (let [paths (form-paths form-layout)
        form  (update form :buffer-init-val #(or % buffer))]
    (-> db
        (assoc-in (:donut.form.layout/buffer paths) (:buffer form))
        (assoc-in (:donut.form.layout/errors paths) (:errors form))
        (assoc-in (:donut.form.layout/input-events paths) (:input-events form))
        (assoc-in (:donut.form.layout/buffer-init-val paths) (:buffer-init-val form))
        (assoc-in (:donut.form.layout/ui-state paths) (:ui-state form)))))

;; Populate form initial state
(dh/rr rf/reg-event-db ::initialize-form
  [rf/trim-v]
  (fn [db [form-layout form]]
    (initialize-form db form-layout form)))

(defn initialize-form-from-path
  [db [form-layout {:keys [data-path data-fn]
                    :or   {data-fn identity}
                    :as   form}]]
  (initialize-form db form-layout (-> form
                                      (assoc :buffer (data-fn (get-in db (dsu/vectorize data-path))))
                                      (dissoc :data-path :data-fn))))

;; Populate form initial state
(dh/rr rf/reg-event-db ::initialize-form-from-path
  [rf/trim-v]
  initialize-form-from-path)

(defn clear-form
  [db form-layout]
  (initialize-form db form-layout nil))

(dh/rr rf/reg-event-db ::clear-form
  [rf/trim-v]
  (fn [db [form-layout]]
    (clear-form db form-layout)))

;; TODO validate clear
(defn clear-selected-keys
  [db form-layout clear]
  (let [paths          (form-paths form-layout)
        paths-to-clear (if (or (= :all clear) (nil? clear))
                         inner-keys
                         clear)]
    (reduce (fn [db k] (assoc-in db k nil))
            db
            (select-keys paths (map #(keyword "donut.form.layout" (name %))
                                    paths-to-clear)))))

(dh/rr rf/reg-event-db ::clear
  [rf/trim-v]
  (fn [db [form-layout clear]]
    (clear-selected-keys db form-layout clear)))

(dh/rr rf/reg-event-db ::keep
  [rf/trim-v]
  (fn [db [form-layout keep-keys]]
    (clear-selected-keys db form-layout (disj (set inner-keys) (set keep-keys)))))

(dh/rr rf/reg-event-db ::replace-with-response
  [rf/trim-v]
  (fn [db [ctx]]
    (let [{:donut.form.layout/keys [buffer buffer-init-val]} (form-paths ctx)
          data                                               (dsf/single-entity ctx)]
      (-> db
          (assoc-in buffer-init-val data)
          (assoc-in buffer data)))))

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
  [{:donut.form/keys [key] :as form-layout} buffer-data sync-opts]
  (let [[method form-handle route-params] key
        route-name (get sync-opts :sync-route-name form-handle)
        method     (get sync-opts :method method)
        params     (merge (:params sync-opts) buffer-data)
        sync-opts  (meta-merge {:default-on   {:success [[::submit-form-success :$ctx]
                                                         [::dsf/default-sync-success :$ctx]]
                                               :fail    [[::submit-form-fail :$ctx]]}
                                :$ctx         form-layout
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
  [db form-layout & [sync-opts]]
  (let [{:donut.form.layout/keys [errors input-events buffer]} (form-paths form-layout)]
    {:db       (-> db
                   (assoc-in errors nil)
                   (update-in (conj input-events :form)
                              (fnil conj #{})
                              :submit))
     :dispatch [::dsf/sync (form-sync-opts form-layout (get-in db buffer) sync-opts)]}))

(dh/rr rf/reg-event-fx ::submit-form
  [rf/trim-v]
  (fn [{:keys [db]} [form-layout sync-opts]]
    (submit-form db form-layout sync-opts)))

;; when user clicks submit on form that has errors
(dh/rr rf/reg-event-db ::register-form-submit
  [rf/trim-v]
  (fn [db [form-layout]]
    (let [{:donut.form.layout/keys [input-events]} (form-paths form-layout)]
      (update-in db
                 (conj input-events :form)
                 (fnil conj #{})
                 :submit))))

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
  (fn [db [ctx]]
    (let [{:donut.form.layout/keys [input-events]} (form-paths ctx)]
      (assoc-in db input-events nil))))

(defn response-error
  [$ctx]
  ;; assumes response-data is something like [:errors {}]
  (get-in $ctx [:resp :response-data 0 1]))

(defn submit-form-fail
  [db [{:donut.form/keys [key]
        :keys            [resp] :as $ctx}]]
  (let [{:donut.form.layout/keys [errors input-events]} (form-paths $ctx)]
    (rfl/console :log "form submit fail:" key resp)
    (-> db
        (assoc-in errors (or (response-error $ctx)
                             {:cause :unknown}))
        (assoc-in input-events nil))))

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
