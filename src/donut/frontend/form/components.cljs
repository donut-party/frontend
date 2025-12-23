(ns donut.frontend.form.components
  "- input just the input element
  - field input element with label and wrapper
  - form subscriptions
  - form system"
  (:require
   [cljs-time.core :as ct]
   [cljs-time.format :as tf]
   [clojure.data :as data]
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
   [re-frame.loggers :as rfl]
   [reagent.core :as r])
  (:require-macros [donut.frontend.form.components]))


;;--------------------
;; specs
;;--------------------

;;---
;; input config
;;---
(def form-layout-keys
  "this is needed for the with form macro.
  TODO figure out better way to do this"
  [:donut.form/key
   :donut.form/sync?
   :donut.form.layout/buffer
   :donut.form.layout/feedback
   :donut.form.layout/input-events
   :donut.form.layout/buffer-init-val
   :donut.form.layout/ui-state])

(def InputConfig
  (into dff/FormLayout
        [[:donut.input/attr-path]
         [:donut.input/format-write]
         [:donut.input/format-read]
         [:donut.form/feedback-fn]]))

(def attr-input-keys (conj dff/form-layout-keys :donut.input/attr-path))

;;---
;; class helpers
;;---

(def classes
  {:donut.field/label-wrapper-class "donut-field-label-wrapper"
   :donut.field/label-class         "donut-field-label"
   :donut.field/required-class      "donut-field-required"})

(defn feedback-classes
  [{:donut.input/keys [attr-feedback]}  & [feedback-class-mapping]]
  (->> @attr-feedback
       (remove #(empty? (second %)))
       (map first)
       (map #(get feedback-class-mapping % (dsu/full-name %)))
       (str/join " ")))

(defn map-feedback-classes
  [mapping]
  (fn [input-opts]
    (feedback-classes input-opts mapping)))

;;---
;; events
;;---

;; TODO make class prefix configurable, e.g. "donut-required" can be
;; "x-required" or just "required"

;; TODO update this with form layout
(defn dispatch-form-input-event
  [form-layout event-type]
  (rf/dispatch [::dff/form-input-event (assoc form-layout :donut.input/event-type event-type)]))

(defn dispatch-attr-input-event
  [dom-event
   {:donut.input/keys [format-write] :as input-config}
   & [update-val?]]
  (rf/dispatch-sync
   [::dff/attr-input-event
    (cond-> (merge (select-keys input-config attr-input-keys))
      true        (merge {:donut.input/event-type (keyword (dcu/go-get dom-event ["type"]))})
      update-val? (merge {:donut.input/value (format-write (dcu/tv dom-event))}))]))

(defn dispatch-new-value
  "Helper when you want non-input elements to update a value"
  [input-config value & [opts]]
  (rf/dispatch-sync
   [::dff/attr-input-event (merge input-config
                                  {:donut.input/value value}
                                  opts)]))

;;--------------------
;; html/react attr helpers
;;--------------------

(defn attr-path-str
  [attr-path]
  (some-> (if (vector? attr-path)
            (last attr-path)
            attr-path)
          name))

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

(def field-opts
  "used in the field component"
  #{:donut.field/tip
    :donut.field/before-input
    :donut.field/after-input
    :donut.field/after-feedback
    :donut.field/label
    :donut.field/no-label?
    :donut.field/class})

(def input-opts
  "react doesn't recognize these and hates them. enumerate them to dissoc them
  from react component"
  (into
   #{:donut.input/attr-buffer
     :donut.input/attr-path
     :donut.input/attr-input-events
     :donut.input/attr-feedback
     :donut.input/select-options
     :donut.input/select-option-components
     :donut.input/form-key
     :donut.input/format-read
     :donut.input/format-write
     :donut.form/feedback-fn}
   dff/form-layout-keys))

(def donut-key-filter
  "used to remove donut keys from react component options"
  (into field-opts input-opts))

(def input-injected-opts
  (->> [:donut.input/attr-path :donut.form/feedback-fn]
       (into dff/form-layout-keys)
       set))

(defn common-input-opts
  "input opts common to all inputs of any type"
  [{:donut.input/keys [attr-path]
    :keys [type]
    :as input-opts}]
  (let [sub-opts (select-keys input-opts input-injected-opts)]
    {:type                          (or type :text)
     :id                            (label-for input-opts)
     :class                         ["donut-input" (attr-path-str attr-path)]
     :donut.input/attr-buffer       (rf/subscribe [::dff/attr-buffer sub-opts])
     :donut.input/attr-feedback     (rf/subscribe [::dffk/attr-feedback sub-opts])
     :donut.input/attr-input-events (rf/subscribe [::dff/attr-input-events sub-opts])}))

(defn merge-common-input-opts
  [input-opts]
  (merge input-opts (common-input-opts input-opts)))

(defn input-opts->react-opts
  [input-opts]
  (let [input-class (:class input-opts)
        opts'       (medley/remove-keys namespace input-opts)]
    (cond-> opts'
      (fn? input-class) (assoc :class (input-class input-opts)))))

(defn default-event-handlers
  [input-opts]
  {:on-change #(dispatch-attr-input-event % input-opts true)
   :on-blur   #(dispatch-attr-input-event % input-opts false)
   :on-focus  #(dispatch-attr-input-event % input-opts false)})

;; begin input-type-opts

(defn merge-default-event-handlers
  [input-opts]
  (merge input-opts (default-event-handlers input-opts)))

(defn input-type-opts-default
  [{:donut.input/keys [attr-buffer format-read format-write] :as input-opts}]
  ;; must defer format-read, format-write, value until this point because
  ;; input-type-opts methods can set the `format-read` appropriate to them
  (let [format-read  (or format-read identity)
        format-write (or format-write identity)]
    (-> input-opts
        (assoc :value (format-read @attr-buffer)
               :donut.input/format-read format-read
               :donut.input/format-write format-write)
        merge-default-event-handlers)))

(defmulti input-type-opts
  "Different input types expect different options and can have different defaults
  for format-read and format-write. For example, a radio button has a `:checked`
  attribute."
  :type)

(defmethod input-type-opts :default
  [input-opts]
  (input-type-opts-default input-opts))

(defmethod input-type-opts :textarea
  [input-opts]
  (input-type-opts-default input-opts))

(defmethod input-type-opts :select
  [input-opts]
  (-> input-opts
      (dc/compose {:donut.input/format-read (dc/or #(or % ""))})
      (input-type-opts-default)))

(defmethod input-type-opts :radio
  [{:donut.input/keys [format-read attr-buffer]
    :keys [value]
    :as opts}]
  (let [format-read (or format-read identity)]
    (-> opts
        (dc/compose {:donut.input/format-write (dc/or (constantly value))})
        (input-type-opts-default)
        (assoc :checked (= value (format-read @attr-buffer))))))

(defmethod input-type-opts :checkbox
  [{:donut.input/keys [attr-buffer format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        value        (format-read @attr-buffer)
        format-write (or format-write (constantly (not value)))]
    (-> opts
        (assoc :donut.input/format-write format-write)
        (input-type-opts-default)
        (merge {:checked (boolean value)})
        (dissoc :value))))

(defmethod input-type-opts :checkbox-set
  [{:keys [value]
    :donut.input/keys [attr-buffer format-read format-write]
    :as opts}]
  (let [format-read  (or format-read identity)
        checkbox-set (or (format-read @attr-buffer) #{})
        format-write (or format-write (constantly (dsu/set-toggle checkbox-set value)))]
    (-> opts
        (assoc :donut.input/format-write format-write)
        input-type-opts-default
        (merge {:type    :checkbox
                :checked (boolean (checkbox-set value))}))))

;; date handling
(defn unparse [fmt x]
  (when x (tf/unparse fmt (js/goog.date.DateTime. x))))

(def date-fmt (:date tf/formatters))

(defn format-write-date
  [v]
  (if (empty? v)
    nil
    (let [parsed (tf/parse date-fmt v)]
      (js/Date. (ct/year parsed) (dec (ct/month parsed)) (ct/day parsed)))))

(defmethod input-type-opts :date
  [{:donut.input/keys [attr-buffer] :as opts}]
  (-> opts
      (assoc :donut.input/format-write format-write-date)
      input-type-opts-default
      (assoc :value (unparse date-fmt @attr-buffer))))

(defn format-write-number
  [v]
  (let [parsed (js/parseInt v)]
    (if (js/isNaN parsed) nil parsed)))

(defmethod input-type-opts :number
  [opts]
  (assoc (input-type-opts-default opts)
         :on-change #(dispatch-attr-input-event
                      %
                      (merge {:donut.input/format-write format-write-number} opts)
                      true)))

(defn merge-input-type-opts
  [input-opts]
  (merge input-opts (input-type-opts input-opts)))

;; end input-type-opts

(defn all-input-opts
  "Top-level coordination of composing input opts.
  Produces default opts at different levels of specificity:
  - common-input-opts are not reified by input type
  - input-type opts specify type-specific behavior, like how selects or checkboxes should work
  - input-opts should be able to override any of these framework defaults"
  [form-config input-type attr-path & [input-opts]]
  (let [passed-in-opts (merge form-config input-opts)
        framework-opts (-> passed-in-opts
                           (merge {:type                  input-type
                                   :donut.input/attr-path attr-path})
                           (merge-common-input-opts)
                           (merge-input-type-opts))
        ks             (->> (data/diff passed-in-opts framework-opts)
                            (take 2)
                            (mapcat keys)
                            set)]
    (dc/compose (select-keys framework-opts ks)
                passed-in-opts)))


;;~~~~~~~~~~~~~~~~~~
;; input components
;;~~~~~~~~~~~~~~~~~~

(defmulti input :type)

(defmethod input :textarea
  [opts]
  [:textarea (input-opts->react-opts opts)])

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

;;---
;; 'field' interface, wraps inputs with messages and labels
;;---

(defn default-feedback-classes
  [feedback]
  (if (or (nil? feedback) (map? feedback))
    (->> feedback
         (medley/filter-vals seq)
         keys
         (map (comp #(str "donut-feedback-" %) name))
         (str/join " ")
         (str " "))
    (rfl/console :warn ::invalid-type (str feedback "should be nil or a map"))))

(defmulti format-attr-feedback (fn [k _v] k))
(defmethod format-attr-feedback :errors
  [_ errors]
  (->> errors
       (map (fn [x] ^{:key (str "donut-error-" x)} [:li x]))
       (into [:ul {:class ["donut-error-messages"]}])))
(defmethod format-attr-feedback :default [_ _] nil)

(defn attr-description
  [feedback]
  (some->> feedback
           (map (fn [[k v]] (format-attr-feedback k v)))
           (filter identity)
           seq
           (into [:div.description])))

(defn field-wrapper-classes
  [{:donut.input/keys [attr-path attr-feedback]}]
  (cond->> ["donut-field-wrapper" (dsu/kebab (attr-path-str attr-path))]
    attr-feedback (into [(default-feedback-classes @attr-feedback)])))

(defmulti field :type)

(defmethod field :default
  [{:keys [required]
    :donut.field/keys [tip no-label?
                       before-input after-input after-feedback]
    :donut.input/keys [attr-feedback]
    :as               opts}]
  (let [composable (dc/composable opts)]
    [:div (composable :donut.field/field-wrapper-opts {:class (field-wrapper-classes opts)})
     (when (or tip (not no-label?))
       [:div (composable :donut.field/label-wrapper-opts {:class [(:donut.field/label-wrapper-class classes)]})
        (when-not no-label?
          [:label
           (composable :donut.field/label-opts {:for (label-for opts) :class [(:donut.field/label-class classes)]})
           (composable :donut.field/label-text (field-label-text opts))
           (when required
             [:span (composable :donut.field/required-wrapper
                                {:class [(:donut.field/required-class classes)]})
              (composable :donut.field/required-text "*")])])
        (when tip
          [:div (composable :donut.field/tip-wrapper {:class [(:donut.field/tip-class classes)]})
           tip])])
     [:div (composable :donut.field/input-wrapper {})
      before-input
      [input opts]
      after-input
      (when attr-feedback (attr-description @attr-feedback))
      after-feedback]]))

(defn checkbox-field
  [{:donut.field/keys [tip required no-label? attr-feedback]
    :as opts}]
  (let [composable (dc/composable opts)]
    [:div (composable :donut.field/field-wrapper-opts {:class (field-wrapper-classes opts)})
     [:div (composable :donut.field/label-wrapper-opts {:class [(:donut.field/label-wrapper-class classes)]})
      (if no-label?
        [:span [input opts] [:i]]
        [:label (composable :donut.field/label-opts {:for (label-for opts) :class [(:donut.field/label-class classes)]})
         [input opts]
         [:i]
         (composable :donut.field/label-text (field-label-text opts))
         (when required
           [:span (composable :donut.field/required-wrapper
                              {:class [(:donut.field/required-class classes)]})
            (composable :donut.field/required-text "*")])])
      (when tip
        [:div (composable :donut.field/tip-wrapper {:class [(:donut.field/tip-class classes)]})
         tip])
      (when attr-feedback (attr-description @attr-feedback))]]))

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
  "Adapts the interface to `field` so that the caller can supply either
  a) a map of opts as the only argument or b) an `input-type`,
  `attr-path`, and `input-opts`.

  In the case of b, `input-opts` consists only of the opts specific to
  this input (it doesn't include framework opts). Those opts are
  passed to the `input-opts` function.

  This allows the user to call [input :text :user/username {:x :y}]
  rather than something like

  [input (all-input-opts form-config :text :user/username {:x :y})]"
  [form-config]
  (fn [input-type & [attr-path input-opts]]
    [field (if (map? input-type)
             (all-input-opts form-config
                             (:type input-type)
                             (:donut.input/attr-path input-type)
                             input-type)
             (all-input-opts form-config input-type attr-path input-opts))]))

;;---
;; interface fns
;;---
(defn submit-when-ready
  [on-submit-handler form-feedback]
  (fn [e]
    (if (:prevent-submit? @form-feedback)
      (.preventDefault e)
      (on-submit-handler e))))

(defn input-component
  "Adapts the interface to `input` so that the caller can supply either
  a) a map of opts as the only argument or b) an `input-type`,
  `attr-path`, and `input-opts`.

  In the case of b, `input-opts` consists only of the opts specific to
  this input (it doesn't include framework opts). Those opts are
  passed to the `all-input-opts-fn` function.

  This allows the developer to write something like

  `[input :text :user/username {:x :y}]`

  rather than something like

  `[input (all-input-opts :form-key :text :user/username {:x :y})]`"
  [form-config]
  (fn [input-type & [attr-path input-opts]]
    [input (if (map? input-type)
             (merge form-config input-type)
             (all-input-opts form-config input-type attr-path input-opts))]))

(defn sync-form
  [form-config & [sync-opts]]
  (when-not (:donut.form/prevent-submit? sync-opts)
    (rf/dispatch [::dff/sync-form form-config sync-opts])))

(defn form-config
  [{:keys [donut.form/key] :as f-config}]
  (merge {:donut.form/sync? true
          :donut.sync/key   key}
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
            :*form-feedback (rf/subscribe [::dffk/form-feedback form-config])
            :*form-buffer   (rf/subscribe [::dff/buffer form-config])
            :*form-dirty?   (rf/subscribe [::dff/form-dirty? form-config])}
    sync? (merge (form-sync-subs (:donut.sync/key form-config)))))

(defn attr-buffer
  [form-config attr-path]
  (rf/subscribe [::dff/attr-buffer (assoc form-config :donut.input/attr-path attr-path)]))

(defn form-components
  [form-config]
  {:*sync-form   (partial sync-form form-config)
   :*input       (input-component form-config)
   :*field       (field-component form-config)
   :*attr-buffer (fn *attr-buffer [attr-path] (attr-buffer form-config attr-path))})

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [form-config]
  (merge (form-subs form-config)
         (form-components form-config)))
