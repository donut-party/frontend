(ns donut.frontend.form.components
  "- input just the input element
  - field input element with label and wrapper
  - form subscriptions
  - form system"
  (:require
   [cljs-time.core :as ct]
   [cljs-time.format :as tf]
   [clojure.set :as set]
   [clojure.string :as str]
   [donut.compose :as dc]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.feedback :as dffk]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.sync.flow :as dsf]
   [donut.sugar.utils :as dsu]
   [medley.core :as medley]
   [re-frame.core :as rf]
   [reagent.core :as r])
  (:require-macros [donut.frontend.form.components]))


;;--------------------
;; specs
;;--------------------

;;---
;; input config
;;---

(def InputConfig
  (into dff/FormConfig
        [[:donut.input/attr-path]
         [:donut.input/format-write]
         [:donut.input/format-read]
         [:donut.form/feedback-fn]]))

(def attr-input-keys (conj dff/form-config-keys :donut.input/attr-path))

;;---
;; class helpers
;;---

(def css-classes
  {:donut.field/field-wrapper-class ["donut-field-field-wrapper"]
   :donut.field/label-wrapper-class ["donut-field-label-wrapper"]
   :donut.field/label-class         ["donut-field-label"]
   :donut.field/required-class      ["donut-field-required"]
   :donut.feedback/error            ["donut-feedback-error"]
   :donut.feedback/warn             ["donut-feedback-warn"]
   :donut.feedback/info             ["donut-feedback-info"]
   :donut.feedback/ok               ["donut-feedback-ok"]})

(defn feedback-css-classes
  [{:donut.form/keys  [feedback-class-mapping]
    :donut.input/keys [attr-feedback]
    :or {feedback-class-mapping css-classes}}]
  (->> @attr-feedback
       (remove #(empty? (second %)))
       (map first)
       (mapv #(get feedback-class-mapping % (dsu/full-name %)))))

;;---
;; events
;;---

(defn dispatch-form-input-event
  [form-config event-type]
  (rf/dispatch [::dff/form-input-event (assoc form-config :donut.input/event-type event-type)]))

(defn dispatch-new-value
  "Helper when you want non-input elements to update a value"
  [input-config value & [opts]]
  (rf/dispatch-sync
   [::dff/attr-input-event (merge input-config
                                  {:donut.input/value value}
                                  opts)]))

(defn dispatch-attr-input-event
  [dom-event input-opts]
  (rf/dispatch-sync
   [::dff/attr-input-event
    (assoc (select-keys input-opts attr-input-keys)
           :donut.input/event-type (keyword (dcu/e-type dom-event)))]))

(defn dispatch-attr-update-value-input-event
  [dom-event {:keys [:donut.input/dom-event->buffer-value] :as input-opts}]
  (rf/dispatch-sync
   [::dff/attr-update-value
    (assoc input-opts
           :donut.input/event-type (keyword (dcu/e-type dom-event))
           :donut.input/value (dom-event->buffer-value dom-event))]))

(defn dispatch-inline-start-editing
  [_dom-event input-opts]
  (rf/dispatch-sync [::dff/inline-editing-start input-opts]))

(defn dispatch-inline-stop-editing
  [_dom-event input-opts]
  (rf/dispatch-sync [::dff/inline-editing-stop input-opts]))

;;--------------------
;; html/react attr helpers
;;--------------------

(defn attr-path-str
  [attr-path]
  (when-let [x (some-> (if (vector? attr-path)
                         (last attr-path)
                         attr-path))]
    (if (keyword? x)
      (name x)
      (str x))))

(defn field-label-text [{:keys [:donut.input/attr-path]}]
  (dsu/kw-title (attr-path-str attr-path)))

(defn label-for
  [{:keys [id]
    :donut.field/keys [form-id]
    :donut.input/keys [attr-path]}]
  (or id
      (str form-id (attr-path-str attr-path))))

(defn input-key
  [{:donut.input/keys [form-id form-key attr-path]} & suffix]
  (str form-id form-key attr-path (str/join "" suffix)))

;;---
;; input opts
;;---

(defn default-base-donut-input-opts
  [{:donut.input/keys [attr-path]
    :keys             [type]
    :as               input-opts}]
  {:type                                 type
   :id                                   (dsu/slugify (label-for input-opts))
   :class                                ["donut-input" (attr-path-str attr-path)]
   :donut.input/attr-buffer              (rf/subscribe [::dff/attr-buffer input-opts])
   :donut.input/attr-feedback            (rf/subscribe [::dff/attr-feedback input-opts])
   :donut.input/attr-input-events        (rf/subscribe [::dff/attr-input-events input-opts])
   :donut.input/attr-buffer-update       (fn [buffer attr-path value]
                                           (assoc-in buffer attr-path value))
   :donut.input/buffer-value->input-opts (fn [v] {:value v})
   :donut.input/dom-event->buffer-value  (fn [dom-event] (dcu/tv dom-event))})

(defmulti base-donut-input-opts-for-type :type)

(defmethod base-donut-input-opts-for-type
  :default
  [input-opts]
  (default-base-donut-input-opts input-opts))

(defmethod base-donut-input-opts-for-type
  :select
  [input-opts]
  (assoc (default-base-donut-input-opts input-opts)
         :donut.input/buffer-value->input-opts (fn [v] {:value (or v "")})))

(defmethod base-donut-input-opts-for-type
  :radio
  [{:keys [value] :as input-opts}]
  (assoc (default-base-donut-input-opts input-opts)
         :donut.input/buffer-value->input-opts (fn [clj-value]
                                                 {:checked (= value clj-value)})
         :donut.input/dom-event->buffer-value (fn [_dom-event] (constantly value))))

(defn input-opts>checked-value
  [input-opts]
  (cond
    (contains? input-opts :donut.input/checked-value) (:donut.input/checked-value input-opts)
    (contains? input-opts :value)                     (:value input-opts)
    :else                                             true))

(defmethod base-donut-input-opts-for-type
  :checkbox
  [input-opts]
  (let [checked-value   (input-opts>checked-value input-opts)
        unchecked-value (get input-opts :donut.input/unchecked-value false)]
    (assoc (default-base-donut-input-opts input-opts)
           :donut.input/checked-value            checked-value
           :donut.input/unchecked-value          unchecked-value
           :donut.input/buffer-value->input-opts (fn [value]
                                                   {:checked (= value checked-value)})
           :donut.input/dom-event->buffer-value  (fn [dom-event]
                                                   (if (dcu/e-checked dom-event)
                                                     checked-value
                                                     unchecked-value)))))

(defmethod base-donut-input-opts-for-type
  :checkbox-set
  [input-opts]
  (let [checked-value (input-opts>checked-value input-opts)]
    (assoc (default-base-donut-input-opts input-opts)
           :type :checkbox
           :donut.input/checked-value            checked-value
           :donut.input/buffer-value->input-opts (fn [value] {:checked ((or value #{}) checked-value)})
           :donut.input/dom-event->buffer-value  (constantly checked-value)
           :donut.input/attr-buffer-update        (fn [buffer attr-path value]
                                                    (update-in buffer attr-path dsu/set-toggle value)))))

(def default-date-fmt (:date tf/formatters))

(defn read-date-formatter
  [fmt]
  (fn [x]
    (when x (tf/unparse fmt (js/goog.date.DateTime. x)))))

(defn write-date-formatter
  [date-fmt]
  (fn [v]
    (if (empty? v)
      nil
      (let [parsed (tf/parse date-fmt v)]
        (js/Date. (ct/year parsed) (dec (ct/month parsed)) (ct/day parsed))))))


(defmethod base-donut-input-opts-for-type :date
  [input-opts]
  (let [date-format  (:donut.input/date-format input-opts default-date-fmt)
        format-read  (read-date-formatter date-format)
        format-write (write-date-formatter date-format)]
    (assoc (default-base-donut-input-opts input-opts)
           :donut.input/date-format date-format
           :donut.input/buffer-value->input-opts (fn [value] {:value (format-read value)})
           :donut.input/dom-event->buffer-value (fn [dom-event] (format-write (dcu/tv dom-event))))))

(defmethod base-donut-input-opts-for-type :number
  [input-opts]
  (assoc (default-base-donut-input-opts input-opts)
         :donut.input/dom-event->buffer-value (fn [dom-event] (dcu/tv-number dom-event))))

(defmethod base-donut-input-opts-for-type :int
  [input-opts]
  (assoc (default-base-donut-input-opts input-opts)
         :type :number
         :donut.input/dom-event->buffer-value (fn [dom-event]
                                                (when-not (str/blank? (dcu/tv dom-event))
                                                  (.floor js/Math (dcu/tv-number dom-event))))))

;; TODO dissoc-when

(defn derived-donut-input-opts
  [{:donut.input/keys [attr-buffer buffer-value->input-opts] :as _base}]
  (buffer-value->input-opts @attr-buffer))

(defn input-opts->react-opts
  [input-opts]
  (let [input-class (:class input-opts)
        opts'       (->> input-opts
                         (medley/remove-keys namespace)
                         (medley/remove-keys #{:route-name}))]
    (cond-> opts'
      (fn? input-class) (assoc :class (input-class input-opts)))))

(defmulti input-event-handlers :donut.input/interaction-mode)

(defmethod input-event-handlers
  :default
  [input-opts]
  {:on-change #(dispatch-attr-update-value-input-event % input-opts)
   :on-blur   #(dispatch-attr-input-event % input-opts)
   :on-focus  #(dispatch-attr-input-event % input-opts)})

(defmethod input-event-handlers
  ::inline
  [input-opts]
  {:on-change #(dispatch-attr-update-value-input-event % input-opts)
   :on-blur   (fn [e]
                (dispatch-attr-input-event e input-opts)
                (dispatch-inline-stop-editing e input-opts))
   :on-focus  (fn [e]
                (dispatch-attr-input-event e input-opts)
                (dispatch-inline-start-editing e input-opts))})

(defn all-input-opts
  "Top-level coordination of composing input opts.
  Produces default opts at different levels of specificity:
  - common-input-opts are not reified by input type
  - input-type opts specify type-specific behavior, like how selects or checkboxes should work
  - input-opts should be able to override any of these framework defaults"
  [form-config input-opts]
  (let [passed-in-opts (merge form-config input-opts)
        base           (base-donut-input-opts-for-type passed-in-opts)
        derived        (derived-donut-input-opts base)
        framework-opts (dc/compose-contained (merge base derived) (dissoc passed-in-opts :type))
        input-handlers (dc/compose-contained (input-event-handlers (merge passed-in-opts framework-opts))
                                             passed-in-opts)
        all-fw-opts    (merge framework-opts input-handlers)]
    (merge passed-in-opts all-fw-opts)))


;;---
;; input components
;;---

(defmulti input :type)

(defmethod input :textarea
  [input-opts]
  [:textarea (input-opts->react-opts input-opts)])

(defmethod input :select
  [opts]
  (let [ref (atom nil)
        ref-fn #(reset! ref %)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (rf/dispatch [::dff/attr-init-buffer (assoc opts :value (dcu/go-get @ref "value"))]))

      :reagent-render
      (fn [{:donut.input/keys [select-options
                               select-option-components]
            :as   opts}]
        (let [input-opts (merge (input-opts->react-opts opts)
                                {:ref ref-fn})]
          (if select-option-components
            (into [:select input-opts] select-option-components)
            [:select input-opts
             (for [[opt-value txt option-opts] select-options]
               ^{:key (input-key opts opt-value)}
               [:option (cond-> {}
                          opt-value (assoc :value opt-value)
                          true      (merge option-opts))
                txt])])))})))

(defmethod input :default
  [opts]
  [:input (input-opts->react-opts opts)])

(defn input-component
  "build an input with framework opts"
  [form-config]
  (fn [input-opts]
    [input (all-input-opts form-config input-opts)]))

;;---
;; 'field' interface, wraps inputs with messages and labels
;;---

(defmulti format-attr-feedback
  (fn [feedback-key _messages] feedback-key))

(defmethod format-attr-feedback :donut.feedback/error
  [_ messages]
  (->> messages
       (map (fn [x] [:li {:key (str "donut-feedback-" x)} x]))
       (into [:ul {:class ["donut-feedback-error"]}])))

(defmethod format-attr-feedback
  :default
  [feedback-key messages]
  (->> messages
       (map (fn [x] ^{:key (str "donut-feedback-" x)} [:li x]))
       (into [:ul {:class [(str "donut-feedback-" (name feedback-key))]}])))

(defn feedback-messages
  [feedback]
  (some->> (seq feedback)
           (map (fn [[k v]] (format-attr-feedback k v)))
           (filter identity)
           (into [:div])))

(defn feedback-messages-component
  [composable feedback]
  (when (seq feedback)
    [:div (composable :donut.field/feedback-messages-wrapper-opts
                      {:class (:donut.field/feedback-messages-wrapper-class css-classes)})
     [feedback-messages feedback]]))

(defn field-wrapper-classes
  [{:donut.input/keys [attr-path] :as input-opts}]
  (-> (feedback-css-classes input-opts)
      (into (:donut.field/field-wrapper-class css-classes))
      (conj (str "donut-field-for-" (dsu/kebab (attr-path-str attr-path))))))

(defmulti field :type)

(defmethod field :default
  [{:keys [required]
    :donut.field/keys [tip no-label? before-input after-input after-feedback]
    :donut.input/keys [attr-feedback]
    :as               opts}]
  (let [composable (dc/composable opts)]
    [:div (composable :donut.field/field-wrapper-opts {:class (field-wrapper-classes opts)})
     (when (or tip (not no-label?))
       [:div (composable :donut.field/label-wrapper-opts {:class (:donut.field/label-wrapper-class css-classes)})
        (when-not no-label?
          [:label
           (composable :donut.field/label-opts {:for (label-for opts) :class (:donut.field/label-class css-classes)})
           (composable :donut.field/label-text (field-label-text opts))
           (when required
             [:span (composable :donut.field/required-wrapper-opts
                                {:class (:donut.field/required-class css-classes)})
              (composable :donut.field/required-text "*")])])
        (when tip
          [:div (composable :donut.field/tip-wrapper-opts {:class (:donut.field/tip-class css-classes)})
           tip])])
     [:div (composable :donut.field/input-wrapper-opts {})
      before-input
      [input opts]
      after-input
      [feedback-messages-component composable @attr-feedback]
      after-feedback]]))

(defn checkbox-field
  [{:donut.field/keys [tip required no-label? attr-feedback]
    :as opts}]
  (let [composable (dc/composable opts)]
    [:div (composable :donut.field/field-wrapper-opts {:class (field-wrapper-classes opts)})
     [:div (composable :donut.field/label-wrapper-opts {:class (:donut.field/label-wrapper-class css-classes)})
      (if no-label?
        [:span [input opts] [:i]]
        [:label (composable :donut.field/label-opts {:for (label-for opts) :class (:donut.field/label-class css-classes)})
         [input opts]
         [:i]
         (composable :donut.field/label-text (field-label-text opts))
         (when required
           [:span (composable :donut.field/required-wrapper-opts
                              {:class (:donut.field/required-class css-classes)})
            (composable :donut.field/required-text "*")])])
      (when tip
        [:div (composable :donut.field/tip-wrapper-opts {:class (:donut.field/tip-class css-classes)})
         tip])
      [feedback-messages-component composable @attr-feedback]]]))

(defmethod field :checkbox
  [opts]
  (checkbox-field opts))

(defmethod field :checkbox-set
  [opts]
  (checkbox-field opts))

(defmethod field :radio
  [opts]
  (checkbox-field opts))

(defn field-component
  "build a field with framework opts"
  [form-config]
  (fn [field-opts]
    [field (all-input-opts form-config field-opts)]))

;;---
;; interface fns
;;---

(defn submit-when-ready
  [on-submit-handler form-feedback]
  (fn [e]
    (if (:prevent-submit? @form-feedback)
      (.preventDefault e)
      (on-submit-handler e))))

(defn input-builder
  "Used when you want to access both the input component and the opts used to
  create that component"
  [form-config]
  (fn [input-opts]
    (let [input-opts (all-input-opts form-config input-opts)]
      {:input      [input input-opts]
       :input-opts input-opts})))

(defn sync-form
  [form-config & [sync-opts]]
  (when-not (:donut.form/prevent-submit? sync-opts)
    (rf/dispatch [::dff/sync-form (merge form-config sync-opts)])))

(defn form-config
  [{:keys [donut.form/key] :as f-config}]
  (dc/compose {:donut.form/sync?       true
               :donut.form/feedback-fn dffk/stored-error-feedback
               :donut.sync/req         {:donut.sync/key key}}
              f-config))

(defn form-sync-subs
  [sync-key]
  (set/rename-keys (dsf/sync-subs sync-key)
                   {:sync-state    :*sync-state
                    :sync-active?  :*sync-active?
                    :sync-success? :*sync-success?
                    :sync-fail?    :*sync-fail?}))

(defn form-subs
  [{:keys [:donut.form/sync?] :as form-config}]
  (cond->  {:*form-ui-state (rf/subscribe [::dff/ui-state form-config])
            :*form-feedback (rf/subscribe [::dff/form-feedback form-config])
            :*form-buffer   (rf/subscribe [::dff/buffer form-config])
            :*form-dirty?   (rf/subscribe [::dff/form-dirty? form-config])}
    sync? (merge (form-sync-subs (:donut.sync/key form-config)))))

(defn attr-buffer
  [form-config attr-path]
  (rf/subscribe [::dff/attr-buffer (assoc form-config :donut.input/attr-path attr-path)]))

(defn form-components
  [form-config]
  {:*sync-form     (partial sync-form form-config)
   :*input         (input-component form-config)
   :*input-opts    (partial all-input-opts form-config)
   :*input-builder (input-builder form-config)
   :*field         (field-component form-config)
   :*attr-buffer   (fn *attr-buffer [attr-path] (attr-buffer form-config attr-path))})

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [form-config]
  (merge (form-subs form-config)
         (form-components form-config)))
