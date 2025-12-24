(ns donut.frontend.form.flow
  (:require
   [donut.compose :as dc]
   [donut.frontend.events :as dfe]
   [donut.frontend.nav.utils :as dnu]
   [donut.frontend.path :as p]
   [donut.frontend.sync.flow :as dsf]
   [donut.sugar.utils :as dsu]
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
(def FormInlineEditing [:map])

(def Form
  [:map
   [:buffer FormBuffer]
   [:feedback {:optional true} [:map-of keyword? BufferView]]
   [:input-events {:optional true} FormInputEvents]
   [:buffer-init-val FormBufferInitVal]
   [:ui-state {:optional true} FormUIState]
   [:inline-editing {:optional true} FormInlineEditing]])

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

(def FormConfig
  [:map
   [:donut.form/key :any]
   [:donut.form/sync? :boolean]
   [:donut.form/initial-state map?]
   [:donut.form/feedback-class-mapping]
   [:donut.form.layout/buffer {:optional true} [:vector keyword?]]
   [:donut.form.layout/feedback {:optional true} [:vector keyword?]]
   [:donut.form.layout/input-events {:optional true} [:vector keyword?]]
   [:donut.form.layout/buffer-init-val {:optional true} [:vector keyword?]]
   [:donut.form.layout/ui-state {:optional true} [:vector keyword?]]
   [:donut.form.layout/inline-editing {:optional true} [:vector keyword?]]])

(def form-config-keys (mapv first (rest FormConfig)))

(defn form-paths
  "By default all form data lives under [:donut :form form-key]. A form layout
  lets you specify different locations for form facets. This function translates
  your form layout into the actual paths to be used for subscriptions and
  events."
  [form-config]
  (let [[form-key-key & layout-keys] form-config-keys
        form-key                     (form-key-key form-config)]
    (reduce (fn [m k]
              (assoc m k (or (k form-config)
                             (p/form-path [form-key (keyword (name k))]))))
            {}
            layout-keys)))

;;--------------------
;; Form subs
;;--------------------

(defn merge-initial-state
  [form-config form]
  (dsu/deep-merge (:donut.form/initial-state form-config) (or form {})))

(rf/reg-sub ::form
  (fn [db [_ {:donut.form.layout/keys [buffer feedback input-events buffer-init-val ui-state] :as form-layout}]]
    (if (or buffer feedback input-events buffer-init-val ui-state)
      (let [{:donut.form.layout/keys [buffer feedback input-events buffer-init-val ui-state]}
            (form-paths form-layout)]
        ;; TODO this could be a drag on performance. could do this more precisely.
        (merge-initial-state
         form-layout
         {:buffer          (get-in db buffer)
          :feedback        (get-in db feedback)
          :input-events    (get-in db input-events)
          :buffer-init-val (get-in db buffer-init-val)
          :ui-state        (get-in db ui-state)}))
      (merge-initial-state
       form-layout
       (p/get-path db :form [(:donut.form/key form-layout)])))))

(defn form-signal
  [[_ form-config]]
  (rf/subscribe [::form form-config]))

(def sub-name->inner-key
  {::buffer          :buffer
   ::feedback        :feedback
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
  (fn [[_ form-config]]
    (rf/subscribe [facet form-config])))

(rf/reg-sub ::attr-buffer
  (attr-facet-sub ::buffer)
  (fn [buffer [_ {:donut.input/keys [attr-path]}]]
    (get-in buffer (dsu/vectorize attr-path))))

(rf/reg-sub ::attr-feedback
  (attr-facet-sub ::feedback)
  (fn [feedback [_ {:donut.input/keys [attr-path]}]]
    (get-in feedback (into [:attrs] (dsu/vectorize attr-path)))))

(rf/reg-sub ::form-feedback
  (attr-facet-sub ::feedback)
  (fn [feedback _]
    (:form feedback)))

(rf/reg-sub ::attr-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events [_ {:donut.input/keys [attr-path]}]]
    (get-in input-events (into [:attrs] (dsu/vectorize attr-path)))))

(rf/reg-sub ::form-input-events
  (attr-facet-sub ::input-events)
  (fn [input-events _]
    (:form input-events)))

;; Has the user modified the buffer?
(rf/reg-sub ::form-dirty?
  (fn [[_ form-config]]
    [(rf/subscribe [::buffer-init-val form-config])
     (rf/subscribe [::buffer form-config])])
  (fn [[buffer-init-val buffer]]
    (not= buffer-init-val buffer)))

;;------
;; Interacting with forms
;;------

(defn attr-input-event
  "Meant to handle all input events: focus, blur, change, etc"
  [db [{:donut.input/keys [attr-path value event-type]
        :as               opts}]]
  (let [{:donut.form.layout/keys [buffer input-events]} (form-paths opts)]
    (cond-> db
      event-type
      (update-in (conj input-events (dsu/vectorize attr-path))
                 (fnil conj #{})
                 event-type)

      (contains? opts :donut.input/value)
      (assoc-in (into buffer (dsu/vectorize attr-path))
                value))))

(rf/reg-event-db ::attr-input-event
  [rf/trim-v]
  attr-input-event)

(rf/reg-event-db ::inline-start-editing
  [rf/trim-v]
  (fn [db [{:donut.input/keys [attr-path]
            :as input-opts}]]
    (let [{:donut.form.layout/keys [buffer inline-editing]} (form-paths input-opts)
          attr-path (dsu/vectorize attr-path)]
      (assoc-in db
                (into inline-editing attr-path)
                (get-in db (into buffer attr-path))))))

(rf/reg-event-fx ::inline-stop-editing
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [{:donut.input/keys [attr-path]
                                :as               input-opts}]]
    (let [{:donut.form.layout/keys [inline-editing buffer]} (form-paths input-opts)
          attr-path                                         (dsu/vectorize attr-path)
          inline-stored-value                               (get-in db (into inline-editing attr-path))
          current-value                                     (get-in db (into buffer attr-path))]
      (cond-> {:db (update-in db
                              (into inline-editing (butlast attr-path))
                              dissoc
                              (last attr-path))}
        (not= inline-stored-value current-value) (assoc :dispatch [::sync-form input-opts (dissoc input-opts :type)])))))

(defn attr-set-value
  "directly set the value of an attribute"
  [db [{:donut.input/keys [attr-path] :as form-config} value]]
  (let [{:donut.form.layout/keys [buffer]} (form-paths form-config)]
    (assoc-in db (into buffer (dsu/vectorize attr-path))
              value)))

(rf/reg-event-db ::attr-set-value
  [rf/trim-v]
  attr-set-value)

(defn attr-dissoc
  "remove attribute from buffer"
  [db [form-config attr-path]]
  (let [{:donut.form.layout/keys [buffer]} (form-paths form-config)
        db-path (into buffer (dsu/vectorize attr-path))]
    (update-in db (butlast db-path) dissoc (last db-path))))

(rf/reg-event-db ::attr-dissoc
  [rf/trim-v]
  attr-dissoc)

(defn form-input-event
  "conj an event-type onto the form's `:input-events`"
  [db [{:keys [:donut.input/event-type] :as form-config}]]
  (let [paths (form-paths form-config)]
    (update-in db
               (:donut.form.layout/input-events paths)
               (fnil conj #{})
               event-type)))

(rf/reg-event-db ::form-input-event
  [rf/trim-v]
  form-input-event)

(rf/reg-event-db ::attr-init-buffer
  [rf/trim-v]
  (fn [db [{:donut.input/keys [attr-path value format-write]
            :as opts}]]
    (let [{:donut.form.layout/keys [buffer]} (form-paths opts)]
      (update-in db (into buffer (dsu/vectorize attr-path)) #(or % (format-write value))))))

;;---------------------
;; Setting form data
;;---------------------

(defn reset-form-buffer
  "Reset buffer to value when form was initialized. Typically paired with a 'reset' button"
  [db form-layout]
  (let [{:donut.form.layout/keys [buffer buffer-init-val]} (form-paths form-layout)]
    (assoc-in db buffer (get-in db buffer-init-val))))

(rf/reg-event-db ::reset-form-buffer
  [rf/trim-v]
  reset-form-buffer)

(defn set-form
  [db form-layout {:keys [buffer] :as form}]
  (let [paths (form-paths form-layout)
        form  (update form :buffer-init-val #(or % buffer))]
    (-> db
        (assoc-in (:donut.form.layout/buffer paths) (:buffer form))
        (assoc-in (:donut.form.layout/feedback paths) (:feedback form))
        (assoc-in (:donut.form.layout/input-events paths) (:input-events form))
        (assoc-in (:donut.form.layout/buffer-init-val paths) (:buffer-init-val form))
        (assoc-in (:donut.form.layout/ui-state paths) (:ui-state form)))))

;; Populate form initial state
(rf/reg-event-db ::set-form
  [rf/trim-v]
  (fn [db [form-layout form]]
    (set-form db form-layout form)))

(defn set-form-from-path
  [db [form-layout {:keys [data-path data-fn]
                    :or   {data-fn identity}
                    :as   form}]]
  (set-form db form-layout (-> form
                               (assoc :buffer (data-fn (get-in db (dsu/vectorize data-path))))
                               (dissoc :data-path :data-fn))))

;; Populate form initial state
(rf/reg-event-db ::set-form-from-path
  [rf/trim-v]
  set-form-from-path)

(defn clear-form
  [db form-config]
  (set-form db form-config nil))

(rf/reg-event-db ::clear-form
  [rf/trim-v]
  (fn [db [{::keys [form-config]}]]
    (clear-form db form-config)))

;; TODO validate clear
(defn clear-selected-keys
  [db form-layout clear]
  (let [paths          (form-paths form-layout)
        paths-to-clear (if (or (= :all clear) (nil? clear))
                         inner-keys
                         clear)]
    (reduce-kv (fn [db _ path] (assoc-in db path nil))
               db
               (select-keys paths (map #(keyword "donut.form.layout" (name %))
                                       paths-to-clear)))))

(rf/reg-event-db ::clear
  [rf/trim-v]
  (fn [db [form-layout clear]]
    (clear-selected-keys db form-layout clear)))

(rf/reg-event-db ::keep
  [rf/trim-v]
  (fn [db [form-layout keep-keys]]
    (clear-selected-keys db form-layout (disj (set inner-keys) (set keep-keys)))))

(rf/reg-event-db ::replace-with-response
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

(defn form-event-defaults
  [form-config]
  (dc/compose
   dsf/default-callbacks
   { ;; by default don't allow a form to be submitted
    ;; when we're waiting for a response
    ::dfe/default {:on {:success (dc/into [[::dsf/default-sync-success]
                                           [::submit-form-sync-success]])
                        :fail    (dc/into [[::dsf/default-sync-fail]
                                           [::submit-form-sync-fail]])}}
    ::dfe/merge   {::form-config form-config}
    ::dfe/pre     [dsf/not-active]}))

(defn form-req
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
  [form-config buffer-data req]
  (dc/compose (form-event-defaults form-config)
              (update req :params merge buffer-data)))

(defn sync-form
  "build form request. update db with :submit input event for form"
  [db form-config & [sync-opts]]
  (let [{:donut.form.layout/keys [feedback input-events buffer]} (form-paths form-config)]
    {:db       (-> db
                   (assoc-in (conj feedback :errors) nil)
                   (update-in (conj input-events :form)
                              (fnil conj #{})
                              :submit))
     :dispatch [::dsf/sync (form-req form-config
                                     (dsu/deep-merge
                                      (:buffer (:donut.form/initial-state form-config))
                                      (get-in db buffer))
                                     sync-opts)]}))

(rf/reg-event-fx ::sync-form
  [rf/trim-v]
  (fn [{:keys [db]} [form-layout sync-opts]]
    (sync-form db form-layout sync-opts)))

(rf/reg-event-db ::record-form-submit
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
#_
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

(defn response-error
  [event-opts]
  ;; assumes response-data is something like [:errors {}]
  (get-in event-opts [::dsf/resp :response-data 0 1]))

(rf/reg-event-db ::submit-form-sync-success
  [rf/trim-v]
  (fn [db [{:keys [::dsf/req]}]]
    (let [{:donut.form.layout/keys [input-events]} (form-paths (::form-layout req))]
      (assoc-in db input-events nil))))

(defn submit-form-sync-fail
  [db [{:donut.form/keys [key]
        :keys            [::dsf/resp]
        :as              event-opts}]]
  (let [{:donut.form.layout/keys [feedback input-events]} (form-paths event-opts)]
    (rfl/console :log "form submit fail:" key resp)
    (-> db
        (assoc-in (conj feedback :errors) (or (response-error event-opts)
                                              {:cause :unknown}))
        (assoc-in input-events nil))))

(rf/reg-event-db ::submit-form-sync-fail
  [rf/trim-v]
  submit-form-sync-fail)

;;--------------------
;; fun little helpers
;;--------------------

(rf/reg-event-db ::set-form-from-sync
  [rf/trim-v]
  (fn [db [{:keys [::dsf/req] :as sync-response}]]
    (set-form db (::form-layout req) {:buffer (dsf/single-entity sync-response)})))

(defn set-form-with-routed-entity
  [db form-config entity-key param-key & [form-opts]]
  (let [ent (dnu/routed-entity db entity-key param-key)]
    (set-form db
              form-config
              (merge {:buffer ent} form-opts))))

;; example:
;; [::dff/set-form-from-sync {:donut.form/key [:put :option]} :option :option/id {:ui-state true}]
(rf/reg-event-db ::set-form-with-routed-entity
  [rf/trim-v]
  (fn [db [form-layout entity-key param-key form-opts]]
    (set-form-with-routed-entity db form-layout entity-key param-key form-opts)))

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
