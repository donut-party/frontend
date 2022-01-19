(ns donut.frontend.form.components
  (:require
   [cljs-time.core :as ct]
   [cljs-time.format :as tf]
   [clojure.string :as str]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.feedback :as dffk]
   [donut.frontend.form.flow :as dff]
   [donut.sugar.utils :as dsu]
   [medley.core :as medley]
   [re-frame.core :as rf]
   [re-frame.loggers :as rfl])
  (:require-macros [donut.frontend.form.components]))

;; TODO make class prefix configurable, e.g. "donut-required" can be
;; "x-required" or just "required"

(defn dispatch-form-input-event
  [form-path event-type]
  (rf/dispatch [::dff/form-input-event {:partial-form-path form-path
                                        :event-type        event-type}]))

(defn dispatch-attr-input-event
  [dom-event {:keys [format-write] :as input-opts} & [update-val?]]
  (rf/dispatch-sync
   [::dff/attr-input-event
    (cond-> (select-keys input-opts [:partial-form-path
                                     :attr-path])
      true        (merge {:event-type (keyword (dcu/go-get dom-event ["type"]))})
      update-val? (merge {:val (format-write (dcu/tv dom-event))}))]))

(defn dispatch-new-val
  "Helper when you want non-input elements to update a val"
  [form-path attr-path val & [opts]]
  (rf/dispatch-sync
   [::dff/attr-input-event (merge {:partial-form-path form-path
                                    :attr-path         attr-path
                                    :event-type        nil
                                    :val               val}
                                   opts)]))

(defn attr-path-str
  [attr-path]
  (some-> (if (vector? attr-path)
            (last attr-path)
            attr-path)
          name))

(defn label-text [attr]
  (dsu/kw-title (attr-path-str attr)))

(defn label-for [form-id attr-path]
  (str form-id (attr-path-str attr-path)))

(defn input-key
  [{:keys [form-id partial-form-path attr-path]} & suffix]
  (str form-id partial-form-path attr-path (str/join "" suffix)))

;; composition helpers
(defn pre-wrap
  [f1 f2]
  (fn [& args]
    (apply f2 args)
    (apply f1 args)))

(defn post-wrap
  [f1 f2]
  (fn [& args]
    (apply f1 args)
    (apply f2 args)))

;;~~~~~~~~~~~~~~~~~~
;; input opts
;;~~~~~~~~~~~~~~~~~~

;; used in the field component below
(def field-opts #{:tip
                  :before-input
                  :after-input
                  :after-feedback
                  :label
                  :no-label?})

;; react doesn't recognize these and hates them
(def input-opts #{:attr-buffer
                  :attr-path
                  :attr-input-events
                  :attr-feedback
                  :options
                  :partial-form-path
                  :input-type
                  :feedback-fns
                  :format-read
                  :format-write})

(defn dissoc-input-opts
  [x]
  (apply dissoc x input-opts))

(defn dissoc-field-opts
  [x]
  (apply dissoc x field-opts))

(defn framework-input-opts
  [{:keys [attr-path
           partial-form-path
           feedback-fns] :as opts}]
  (merge {:attr-buffer       (rf/subscribe [::dff/attr-buffer partial-form-path attr-path])
          :attr-feedback     (rf/subscribe [::dffk/attr-feedback
                                            partial-form-path
                                            attr-path
                                            feedback-fns])
          :attr-input-events (rf/subscribe [::dff/attr-input-events partial-form-path attr-path])}
         opts))

(defn default-event-handlers
  [opts]
  {:on-change #(dispatch-attr-input-event % opts true)
   :on-blur   #(dispatch-attr-input-event % opts false)
   :on-focus  #(dispatch-attr-input-event % opts false)})

(defn merge-event-handlers
  [opts]
  (merge-with (fn [framework-handler custom-handler]
                (fn [e]
                  (custom-handler e framework-handler opts)))
              (default-event-handlers opts)
              opts))

(defn input-type-opts-default
  [{:keys [form-id attr-path attr-buffer input-type]
    :as   opts}]
  (let [{:keys [format-read] :as opts} (merge {:format-read  identity
                                               :format-write identity}
                                              opts)]
    (-> {:type      (name (or input-type :text))
         :value     (format-read @attr-buffer)
         :id        (label-for form-id attr-path)
         :class     (str "donut-input " (attr-path-str attr-path))}
        (merge opts)
        (merge-event-handlers))))

(defmulti input-type-opts
  "Different input types expect different options. For example, a radio
  button has a `:checked` attribute."
  :input-type)

(defmethod input-type-opts :default
  [opts]
  (input-type-opts-default opts))

(defmethod input-type-opts :textarea
  [opts]
  (-> (input-type-opts-default opts)
      (dissoc :type)))

(defmethod input-type-opts :select
  [opts]
  (-> opts
      (update :format-read (fn [f] (or f #(or % ""))))
      (input-type-opts-default)
      (dissoc :type)))

(defmethod input-type-opts :radio
  [{:keys [format-read format-write attr-buffer value] :as opts}]
  (let [format-read  (or format-read identity)
        format-write (or format-write (constantly value))]
    (assoc (input-type-opts-default (merge opts {:format-write format-write}))
           :checked (= value (format-read @attr-buffer)))))

(defmethod input-type-opts :checkbox
  [{:keys [attr-buffer format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        value        (format-read @attr-buffer)
        format-write (or format-write (constantly (not value)))]
    (-> (input-type-opts-default opts)
        (merge {:checked   (boolean value)
                :on-change #(dispatch-attr-input-event % (merge opts {:format-write format-write}) true)})
        (dissoc :value))))

(defn toggle-set-membership
  [s v]
  (let [new-s ((if (s v) disj conj) s v)]
    (if (empty? new-s) #{} new-s)))

(defmethod input-type-opts :checkbox-set
  [{:keys [attr-buffer value format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        checkbox-set (or (format-read @attr-buffer) #{})
        format-write (or format-write (constantly (toggle-set-membership checkbox-set value)))]
    (merge (input-type-opts-default opts)
           {:type      "checkbox"
            :checked   (boolean (checkbox-set value))
            :on-change #(dispatch-attr-input-event % (merge opts {:format-write format-write}) true)})))

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
  [{:keys [attr-buffer] :as opts}]
  (assoc (input-type-opts-default opts)
         :value (unparse date-fmt @attr-buffer)
         :on-change #(dispatch-attr-input-event % (merge {:format-write format-write-date} opts) true)))

(defn format-write-number
  [v]
  (let [parsed (js/parseInt v)]
    (if (js/isNaN parsed) nil parsed)))

(defmethod input-type-opts :number
  [opts]
  (assoc (input-type-opts-default opts)
         :on-change #(dispatch-attr-input-event % (merge {:format-write format-write-number} opts) true)))

;;~~~~~~~~~~~~~~~~~~
;; input components
;;~~~~~~~~~~~~~~~~~~

(defmulti input :input-type)

(defmethod input :textarea
  [opts]
  [:textarea (dissoc-input-opts opts)])

(defmethod input :select
  [{:keys [options]
    :as   opts}]
  [:select (dissoc-input-opts opts)
   (for [[opt-value txt option-opts] options]
     ^{:key (input-key opts opt-value)}
     [:option (cond-> {}
                opt-value (assoc :value opt-value)
                true      (merge option-opts))
      txt])])

(defmethod input :default
  [opts]
  [:input (dissoc-input-opts opts)])

;;~~~~~~~~~~~~~~~~~~
;; 'field' interface, wraps inputs with messages and labels
;;~~~~~~~~~~~~~~~~~~

(defn feedback-classes
  [feedback]
  (if (or (nil? feedback) (map? feedback))
    (->> feedback
         (medley/filter-vals seq)
         keys
         (map (comp #(str "donut-" %) name))
         (str/join " ")
         (str " "))
    (rfl/console :warn ::invalid-type (str feedback "should be nil or a map"))))

(defmulti format-attr-feedback (fn [k _v] k))
(defmethod format-attr-feedback :errors
  [_ errors]
  (->> errors
       (map (fn [x] ^{:key (str "donut-error-" x)} [:li x]))
       (into [:ul {:class "donut-error-messages"}])))
(defmethod format-attr-feedback :default [_ _] nil)

(defn attr-description
  [feedback]
  (some->> feedback
           (map (fn [[k v]] (format-attr-feedback k v)))
           (filter identity)
           seq
           (into [:div.description])))

(defn field-classes
  [{:keys [attr-path attr-feedback]}]
  (cond->> [(dsu/kebab (attr-path-str attr-path))]
    attr-feedback (into [(feedback-classes @attr-feedback)])
    true      (str/join " ")))

(defmulti field :input-type)

(defmethod field :default
  [{:keys [form-id attr-path attr-feedback
           tip required label no-label?
           before-input after-input after-feedback]
    :as opts}]
  [:div.field {:class (field-classes opts)}
   (when (or tip (not no-label?))
     [:div.field-label
      (when-not no-label?
        [:label {:for (label-for form-id attr-path) :class "donut-label"}
         (or label (label-text attr-path))
         (when required [:span {:class "donut-required"} "*"])])
      (when tip [:div.tip tip])])
   [:div
    before-input
    [input (dissoc-field-opts opts)]
    after-input
    (when attr-feedback (attr-description @attr-feedback))
    after-feedback]])

(defn checkbox-field
  [{:keys [tip required label no-label? attr-path attr-feedback]
    :as opts}]
  [:div.field {:class (field-classes opts)}
   [:div
    (if no-label?
      [:span [input (apply dissoc opts field-opts)] [:i]]
      [:label {:class "donut-label"}
       [input (dissoc-field-opts opts)]
       [:i]
       (or label (label-text attr-path))
       (when required [:span {:class "donut-required"} "*"])])
    (when tip [:div.tip tip])
    (when attr-feedback (attr-description @attr-feedback))]])

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

  [input (all-input-opts :partial-form-path :text :user/username {:x :y})]"
  [all-input-opts-fn]
  (fn [input-type & [attr-path input-opts]]
    [field (if (map? input-type)
             input-type
             (all-input-opts-fn input-type attr-path input-opts))]))

;;~~~~~~~~~~~~~~~~~~
;; interface fns
;;~~~~~~~~~~~~~~~~~~
(defn submit-when-ready
  [on-submit-handler form-feedback]
  (fn [e]
    (if-not (:prevent-submit? @form-feedback)
      (on-submit-handler e)
      (.preventDefault e))))

(defn all-input-opts
  [partial-form-path formwide-opts input-type attr-path & [opts]]
  (-> {:partial-form-path partial-form-path
       :input-type        input-type
       :attr-path         attr-path}
      (merge (:input-opts formwide-opts))
      (merge opts)
      (framework-input-opts)
      (input-type-opts)))

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

  `[input (all-input-opts :partial-form-path :text :user/username {:x :y})]`"
  [all-input-opts-fn]
  (fn [input-type & [attr-path input-opts]]
    [input (if (map? input-type)
             input-type
             (all-input-opts-fn input-type attr-path input-opts))]))

(defn sugar-submit-opts
  "support a couple submit shorthands"
  [submit-opts sync-key]
  (-> (if (vector? submit-opts)
        {:on {:success submit-opts}}
        submit-opts)
      (dsu/move-keys #{:success :fail} [:on])
      (update :sync-key #(or % sync-key))))

(defn submit
  [partial-form-path sync-key & [submit-opts]]
  (let [submit-opts (sugar-submit-opts submit-opts sync-key)]
    (when-not (:prevent-submit? submit-opts)
      (rf/dispatch [::dff/submit-form partial-form-path submit-opts]))))

(defn form-sync-subs
  [sync-key]
  {:*sync-state    (rf/subscribe [::dff/sync-state sync-key])
   :*sync-active?  (rf/subscribe [::dff/sync-active? sync-key])
   :*sync-success? (rf/subscribe [::dff/sync-success? sync-key])
   :*sync-fail?    (rf/subscribe [::dff/sync-fail? sync-key])})

(defn form-subs
  [partial-form-path & [{:keys [feedback-fns *sync-key]}]]
  (merge {:*form-path     partial-form-path
          :*form-ui-state (rf/subscribe [::dff/ui-state partial-form-path])
          :*form-errors   (rf/subscribe [::dff/errors partial-form-path])
          :*form-feedback (rf/subscribe [::dffk/form-feedback feedback-fns])
          :*form-buffer   (rf/subscribe [::dff/buffer partial-form-path])
          :*form-dirty?   (rf/subscribe [::dff/form-dirty? partial-form-path])

          :*state-success? (rf/subscribe [::dff/state-success? partial-form-path])}
         (form-sync-subs *sync-key)))

(defn form-components
  [partial-form-path & [formwide-opts *sync-key]]
  (let [input-opts-fn (partial all-input-opts
                               partial-form-path
                               formwide-opts)]
    {:*submit     (partial submit partial-form-path *sync-key)
     :*input-opts input-opts-fn
     :*input      (input-component input-opts-fn)
     :*field      (field-component input-opts-fn)}))

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [partial-form-path & [formwide-opts]]
  (merge (form-subs partial-form-path formwide-opts)
         (form-components partial-form-path formwide-opts)))
