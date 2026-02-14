(ns donut.frontend.form.flow
  (:require
   [donut.compose :as dc]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.events :as dfe]
   [donut.frontend.form.feedback :as dffk]
   [donut.frontend.nav.utils :as dnu]
   [donut.frontend.path :as p]
   [donut.frontend.sync.flow :as dsf]
   [donut.sugar.utils :as dsu]
   [malli.util :as mu]
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
   [:scratch {:optional true} FormInlineEditing] ;; place internal data
   ])

;;---
;; form config
;;---

(def LayoutPath
  [:vector :keyword])

(def FormLayout
  [:map
   [:buffer {:optional true} LayoutPath]
   [:feedback {:optional true} LayoutPath]
   [:input-events {:optional true} LayoutPath]
   [:buffer-init-val {:optional true} LayoutPath]
   [:ui-state {:optional true} LayoutPath]
   [:scratch {:optional true} [:map-of keyword? BufferView]]])

(def FormConfig
  [:map
   [::form-key :any]
   [::layout FormLayout]
   [::sync? :boolean]
   [::feedback-fn :any]
   [:donut.frontend.form.components/feedback-class-mapping :map]])

(def form-config-keys (mapv first (rest FormConfig)))

;;---
;; helpers
;;---

(defn form-paths
  "By default all form data lives under [:donut :form form-key]. A form layout
  lets you specify different locations for form facets. This function translates
  your form layout into the actual paths to be used for subscriptions and
  events."
  [{::keys [form-key layout]}]
  (reduce (fn [m k]
            (assoc m k (or (k layout)
                           (p/form-path [form-key (keyword (name k))]))))
          {}
          (mu/keys FormLayout)))

(defn form-data
  [db form-config]
  (dcu/merge-retrieved-vals {} db (form-paths form-config)))

(defn form-feedback
  [db {:keys [::feedback-fn] :as form-config}]
  (feedback-fn (form-data db form-config)))


;; TODO helper for attr buffer values

;;--------------------
;; Form subs
;;--------------------

(rf/reg-sub ::form
  (fn [db [_ form-config]]
    (form-data db form-config)))

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
(doseq [[sub-name inner-key] (dissoc sub-name->inner-key ::feedback)]
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
  (fn [buffer [_ {:donut.input/keys [attr-path] :as _opts}]]
    (get-in buffer (dsu/vectorize attr-path))))

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
  "record that a dom event happened: focus, blur, etc"
  [db [{:donut.input/keys [attr-path event-type]
        :as               opts}]]
  (let [{:keys [input-events]} (form-paths opts)]
    (cond-> db
      event-type
      (update-in (conj input-events (dsu/vectorize attr-path))
                 (fnil conj #{})
                 event-type))))

(rf/reg-event-db ::attr-input-event
  [rf/trim-v]
  attr-input-event)

(defn attr-update-value
  "handle events that update a value in a buffer"
  [db [{:donut.input/keys [attr-path attr-buffer-update value event-type]
        :as               opts}]]
  (let [{:keys [buffer input-events]} (form-paths opts)]
    (cond-> db
      event-type
      (update-in (conj input-events (dsu/vectorize attr-path))
                 (fnil conj #{})
                 event-type)

      (contains? opts :donut.input/value)
      (attr-buffer-update (into buffer (dsu/vectorize attr-path)) value))))

(rf/reg-event-db ::attr-update-value
  [rf/trim-v]
  attr-update-value)

(defn inline-editing-paths
  [{:donut.input/keys [attr-path] :as input-opts}]
  (let [{:keys [scratch buffer]} (form-paths input-opts)
        attr-path                (dsu/vectorize attr-path)
        on-focus-path            (->> attr-path
                                      (into [::on-focus-value])
                                      (into scratch))]
    {:buffer-attr-path (into buffer attr-path)
     :on-focus-path    on-focus-path}))

(rf/reg-event-db ::inline-editing-start
  [rf/trim-v]
  (fn [db [input-opts]]
    (let [{:keys [buffer-attr-path on-focus-path]} (inline-editing-paths input-opts)]
      (assoc-in db
                on-focus-path
                (get-in db buffer-attr-path)))))

(defn- dissoc-inline-value
  "stop tracking the focused value"
  [db on-focus-path]
  (update-in db
             (butlast on-focus-path)
             dissoc
             (last on-focus-path)))

(rf/reg-event-fx ::inline-editing-stop
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [input-opts]]
    (let [{:keys [buffer-attr-path on-focus-path]} (inline-editing-paths input-opts)
          inline-stored-value                      (get-in db on-focus-path)
          current-value                            (get-in db buffer-attr-path)
          sync?                                    (and (not= inline-stored-value current-value)
                                                        (not (dffk/has-feedback? (form-feedback db input-opts)
                                                                                 :donut.feedback/error)))]
      (cond-> {:db (dissoc-inline-value db on-focus-path)}
        sync? (assoc :dispatch [::sync-form input-opts])))))

(defn attr-set-value
  "directly set the value of an attribute"
  [db [{:donut.input/keys [attr-path] :as form-config} value]]
  (let [{:keys [buffer]} (form-paths form-config)]
    (assoc-in db (into buffer (dsu/vectorize attr-path))
              value)))

(rf/reg-event-db ::attr-set-value
  [rf/trim-v]
  attr-set-value)

(defn attr-dissoc
  "remove attribute from buffer"
  [db [form-config attr-path]]
  (let [{:keys [buffer]} (form-paths form-config)
        db-path          (into buffer (dsu/vectorize attr-path))]
    (update-in db (butlast db-path) dissoc (last db-path))))

(rf/reg-event-db ::attr-dissoc
  [rf/trim-v]
  attr-dissoc)

(defn form-input-event
  "conj an event-type onto the form's `:input-events`"
  [db [{:keys [:donut.input/event-type] :as form-config}]]
  (let [{:keys [input-events]} (form-paths form-config)]
    (update-in db
               input-events
               (fnil conj #{})
               event-type)))

(rf/reg-event-db ::form-input-event
  [rf/trim-v]
  form-input-event)

(rf/reg-event-db ::attr-init-buffer
  [rf/trim-v]
  (fn [db [{:donut.input/keys [attr-path value]
            :as opts}]]
    (let [{:keys [buffer]} (form-paths opts)]
      (update-in db (into buffer (dsu/vectorize attr-path)) #(or % value)))))

;;---------------------
;; Setting form data
;;---------------------

(defn reset-form-buffer
  "Reset buffer to value when form was initialized. Typically paired with a 'reset' button"
  [db form-config]
  (let [{:keys [buffer buffer-init-val]} (form-paths form-config)]
    (assoc-in db buffer (get-in db buffer-init-val))))

(rf/reg-event-db ::reset-form-buffer
  [rf/trim-v]
  reset-form-buffer)

(defn set-form
  [db {:keys [::set-form] :as form-config}]
  (let [{:keys [buffer]} set-form
        paths            (form-paths form-config)
        form             (update set-form :buffer-init-val #(or % buffer))]
    ;; TODO kind of like dcu/merge-retrieved-vals
    (-> db
        (assoc-in (:buffer paths) (:buffer form))
        (assoc-in (:feedback paths) (:feedback form))
        (assoc-in (:input-events paths) (:input-events form))
        (assoc-in (:buffer-init-val paths) (:buffer-init-val form))
        (assoc-in (:ui-state paths) (:ui-state form)))))

;; Populate form initial state
(rf/reg-event-db ::set-form
  [rf/trim-v]
  (fn [db [opts]]
    (set-form db opts)))

(defn set-form-from-path
  [db [form-config {:keys [data-path data-fn]
                    :or   {data-fn identity}
                    :as   form}]]
  (set-form db (dc/compose {::set-form (-> form
                                           (assoc :buffer (data-fn (get-in db (dsu/vectorize data-path))))
                                           (dissoc :data-path :data-fn))}
                           form-config)))

;; Populate form initial state
(rf/reg-event-db ::set-form-from-path
  [rf/trim-v]
  set-form-from-path)

(defn clear-form
  [db form-config]
  (set-form db (assoc form-config ::set-form nil)))

(rf/reg-event-db ::clear-form
  [rf/trim-v]
  (fn [db [form-config]]
    (clear-form db form-config)))

;; TODO validate clear
(defn clear-selected-keys
  [db form-config clear]
  (let [paths          (form-paths form-config)
        paths-to-clear (if (or (= :all clear) (nil? clear))
                         inner-keys
                         clear)]
    (reduce-kv (fn [db _ path] (assoc-in db path nil))
               db
               (select-keys paths paths-to-clear))))

(rf/reg-event-db ::clear
  [rf/trim-v]
  (fn [db [form-config clear]]
    (clear-selected-keys db form-config clear)))

(rf/reg-event-db ::keep
  [rf/trim-v]
  (fn [db [form-config keep-keys]]
    (clear-selected-keys db form-config (disj (set inner-keys) (set keep-keys)))))

(rf/reg-event-db ::replace-with-response
  [rf/trim-v]
  (fn [db [ctx]]
    (let [{:keys [buffer buffer-init-val]} (form-paths ctx)
          data                             (dsf/single-entity ctx)]
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
    ::dfe/on    {:success (dc/into [[::submit-form-sync-success]])
                 :fail    (dc/into [[::submit-form-sync-fail]])}
    ::dfe/merge form-config
    ::dfe/pre   [dsf/not-active]}))

;; TODO req should be in form-config
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
  - the `::sync-event` key of form spec can customize the sync request"
  [{:keys [::sync-event] :as event-opts} buffer-data]
  (-> event-opts
      (dissoc ::sync-event)
      form-event-defaults
      (dc/compose (update-in sync-event [::dsf/req :params] merge buffer-data))))

(defn sync-form
  "build form request. update db with :submit input event for form"
  [db event-opts]
  (let [{:keys [feedback input-events buffer]} (form-paths event-opts)]
    {:db       (-> db
                   (assoc-in (conj feedback :errors) nil)
                   (update-in (conj input-events :form)
                              (fnil conj #{})
                              :submit))
     :dispatch [::dsf/sync (form-req event-opts (get-in db buffer))]}))

(rf/reg-event-fx ::sync-form
  [rf/trim-v]
  (fn [{:keys [db]} [form-config]]
    (sync-form db form-config)))

(rf/reg-event-db ::record-form-submit
  [rf/trim-v]
  (fn [db [form-config]]
    (let [{:keys [input-events]} (form-paths form-config)]
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
  (fn [db [event]]
    (let [{:keys [input-events]} (form-paths event)]
      (assoc-in db input-events nil))))

(defn submit-form-sync-fail
  [db [{:keys [::dsf/resp ::form-key]
        :as   event-opts}]]
  (let [{:keys [feedback input-events]} (form-paths event-opts)]
    (rfl/console :log "form submit fail:"
                 {:keys     (keys event-opts)
                  :form-key form-key
                  :feedback feedback
                  :resp     resp})
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

;; TODO update this
;; example:
;; [::dff/set-form-from-sync {:donut.form/key [:put :option]} :option :option/id {:ui-state true}]
(rf/reg-event-db ::set-form-from-sync
  [rf/trim-v]
  (fn [db [event-opts]]
    (set-form db (dc/compose {::set-form {:buffer (dsf/single-entity event-opts)}}
                             event-opts))))

(defn set-form-with-routed-entity
  [db form-config entity-key param-key]
  (let [ent (dnu/routed-entity db entity-key param-key)]
    (set-form db (dc/compose {::set-form {:buffer ent}}
                             form-config))))

;; TODO revisit this signature
(rf/reg-event-db ::set-form-with-routed-entity
  [rf/trim-v]
  (fn [db [form-config entity-key param-key]]
    (set-form-with-routed-entity db form-config entity-key param-key)))

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

;;---
;; feedback
;;---

(rf/reg-sub ::feedback
  (fn [[_ form-config]]
    (rf/subscribe [::form form-config]))
  (fn [form-data [_ {:keys [::feedback-fn]}]]
    (when feedback-fn
      (feedback-fn form-data))))

;; yields values of the form
;; {:error [e1 e2 e3]
;;  :info  [i1 i2 i3]
(rf/reg-sub ::form-feedback
  (fn [[_ form-config]]
    (rf/subscribe [::feedback form-config]))
  (fn [feedback _]
    (reduce-kv (fn [form-feedback feedback-type all-feedback]
                 (if-let [feedback (:form all-feedback)]
                   (assoc form-feedback feedback-type feedback)
                   form-feedback))
               {}
               feedback)))

;; yields values of the form
;; {:error [e1 e2 e3]
;;  :info  [i1 i2 i3]
(rf/reg-sub ::attr-feedback
  (fn [[_ form-config _attr-path]]
    (rf/subscribe [::feedback form-config]))
  (fn [feedback [_ {:donut.input/keys [attr-path]}]]
    (reduce-kv (fn [attr-feedback feedback-type all-feedback]
                 (if-let [feedback (get (:attrs all-feedback)
                                        (dsu/vectorize attr-path))]
                   (assoc attr-feedback feedback-type feedback)
                   attr-feedback))
               {}
               feedback)))
