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
  [form-key event-type]
  (rf/dispatch [::dff/form-input-event {:form-key   form-key
                                        :event-type event-type}]))

(defn dispatch-attr-input-event
  [dom-event
   {:donut.input/keys [format-write form-key attr-path]}
   & [update-val?]]
  (rf/dispatch-sync
   [::dff/attr-input-event
    (cond-> {:form-key form-key
             :attr-path attr-path}
      true        (merge {:event-type (keyword (dcu/go-get dom-event ["type"]))})
      update-val? (merge {:val (format-write (dcu/tv dom-event))}))]))

(defn dispatch-new-val
  "Helper when you want non-input elements to update a val"
  [form-key attr-path val & [opts]]
  (rf/dispatch-sync
   [::dff/attr-input-event (merge {:form-key   form-key
                                   :attr-path  attr-path
                                   :event-type nil
                                   :val        val}
                                  opts)]))

(defn attr-path-str
  [attr-path]
  (some-> (if (vector? attr-path)
            (last attr-path)
            attr-path)
          name))

(defn label-text [{:keys [:donut.field/label :donut.input/attr-path]}]
  (cond
    (string? label) label
    label           label
    :else           (dsu/kw-title (attr-path-str attr-path))))

(defn label-for [{:donut.field/keys [form-id]
                  :donut.input/keys [attr-path]}]
  (str form-id (attr-path-str attr-path)))

(defn input-key
  [{:donut.input/keys [form-id form-key attr-path]} & suffix]
  (str form-id form-key attr-path (str/join "" suffix)))

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
(def field-opts #{:donut.field/tip
                  :donut.field/before-input
                  :donut.field/after-input
                  :donut.field/after-feedback
                  :donut.field/label
                  :donut.field/no-label?
                  :donut.field/class})

;; react doesn't recognize these and hates them
(def input-opts #{:donut.input/attr-buffer
                  :donut.input/attr-path
                  :donut.input/attr-input-events
                  :donut.input/attr-feedback
                  :donut.input/select-options
                  :donut.input/select-option-components
                  :donut.input/form-key
                  :donut.input/feedback-fns
                  :donut.input/format-read
                  :donut.input/format-write})

(def all-opts (into field-opts input-opts))

(defn framework-input-opts
  [{:donut.input/keys [attr-path form-key feedback-fns]
    :as               opts}]
  (merge
   #:donut.input{:attr-buffer       (rf/subscribe [::dff/attr-buffer form-key attr-path])
                 :attr-feedback     (rf/subscribe [::dffk/attr-feedback
                                                   form-key
                                                   attr-path
                                                   feedback-fns])
                 :attr-input-events (rf/subscribe [::dff/attr-input-events form-key attr-path])}
   opts))

(defn default-event-handlers
  [opts]
  {:donut.input/on-change #(dispatch-attr-input-event % opts true)
   :donut.input/on-blur   #(dispatch-attr-input-event % opts false)
   :donut.input/on-focus  #(dispatch-attr-input-event % opts false)})

(defn merge-event-handlers
  "Merges custom event handlers with the default framework handlers in such a way
  that the custom handler can access the framework handler and all input opts"
  [opts]
  (merge-with (fn [framework-handler custom-handler]
                (fn [e]
                  (custom-handler e framework-handler opts)))
              (default-event-handlers opts)
              opts))

(defn donut-opts->react-opts
  [opts]
  (->> opts
       (medley/filter-keys (complement all-opts))
       (medley/map-keys (comp keyword name))))

(defn input-type-opts-default
  [{:donut.input/keys [attr-path attr-buffer type]
    :as   opts}]
  (let [{:keys [:donut.input/format-read] :as opts} (merge #:donut.input{:format-read  identity
                                                                         :format-write identity}
                                                           opts)]
    (-> {:donut.input/type  (or type :text)
         :donut.input/id    (label-for opts)
         :donut.input/class (str "donut-input " (attr-path-str attr-path))
         :donut.input/value (format-read @attr-buffer)}
        (merge opts)
        (merge-event-handlers))))

(defmulti input-type-opts
  "Different input types expect different options. For example, a radio
  button has a `:checked` attribute."
  :donut.input/type)

(defmethod input-type-opts :default
  [opts]
  (input-type-opts-default opts))

(defmethod input-type-opts :textarea
  [opts]
  (-> (input-type-opts-default opts)
      (dissoc :donut.input/type)))

(defmethod input-type-opts :select
  [opts]
  (-> opts
      (update :donut.input/format-read (fn [f] (or f #(or % ""))))
      (input-type-opts-default)))

(defmethod input-type-opts :radio
  [{:donut.input/keys [format-read format-write attr-buffer value] :as opts}]
  (let [format-read  (or format-read identity)
        format-write (or format-write (constantly value))]
    (-> (merge opts {:donut.input/format-write format-write})
        (input-type-opts-default)
        (assoc :donut.input/checked (= value (format-read @attr-buffer))))))

(defmethod input-type-opts :checkbox
  [{:donut.input/keys [attr-buffer format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        value        (format-read @attr-buffer)
        format-write (or format-write (constantly (not value)))]
    (-> opts
        (assoc :donut.input/format-write format-write)
        (input-type-opts-default)
        (merge {:donut.input/checked (boolean value)})
        (dissoc :donut.input/value))))

(defn toggle-set-membership
  [s v]
  (let [new-s ((if (s v) disj conj) s v)]
    (if (empty? new-s) #{} new-s)))

(defmethod input-type-opts :checkbox-set
  [{:donut.input/keys [attr-buffer value format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        checkbox-set (or (format-read @attr-buffer) #{})
        format-write (or format-write (constantly (toggle-set-membership checkbox-set value)))]
    (-> opts
        (assoc :donut.input/format-write format-write)
        input-type-opts-default
        (merge {:donut.input/type    :checkbox
                :donut.input/checked (boolean (checkbox-set value))}))))

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
      (assoc :donut.input/value (unparse date-fmt @attr-buffer))))

(defn format-write-number
  [v]
  (let [parsed (js/parseInt v)]
    (if (js/isNaN parsed) nil parsed)))

(defmethod input-type-opts :number
  [opts]
  (assoc (input-type-opts-default opts)
         :donut.input/on-change #(dispatch-attr-input-event
                                  %
                                  (merge {:donut.input/format-write format-write-number} opts)
                                  true)))

;;~~~~~~~~~~~~~~~~~~
;; input components
;;~~~~~~~~~~~~~~~~~~

(defmulti input :donut.input/type)

(defmethod input :textarea
  [opts]
  [:textarea (donut-opts->react-opts opts)])

(defmethod input :select
  [{:donut.input/keys [select-options
                       select-option-components]
    :as   opts}]
  (if select-option-components
    (into [:select (donut-opts->react-opts opts)]
          select-option-components)
    [:select (donut-opts->react-opts opts)
     (for [[opt-value txt option-opts] select-options]
       ^{:key (input-key opts opt-value)}
       [:option (cond-> {}
                  opt-value (assoc :value opt-value)
                  true      (merge option-opts))
        txt])]))

(defmethod input :default
  [opts]
  [:input (donut-opts->react-opts opts)])

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
  [{:donut.input/keys [attr-path attr-feedback]
    :donut.field/keys [class]}]
  (or class
      (cond->> [(dsu/kebab (attr-path-str attr-path))]
        attr-feedback (into [(feedback-classes @attr-feedback)])
        true      (str/join " "))))

(defmulti field :donut.input/type)

(defmethod field :default
  [{:donut.field/keys [tip required no-label?
                       before-input after-input after-feedback]
    :donut.input/keys [attr-feedback]
    :as opts}]
  [:div.field {:class (field-classes opts)}
   (when (or tip (not no-label?))
     [:div.field-label
      (when-not no-label?
        [:label {:for (label-for opts) :class "donut-label"}
         (label-text opts)
         (when required [:span {:class "donut-required"} "*"])])
      (when tip [:div.tip tip])])
   [:div
    before-input
    [input opts]
    after-input
    (when attr-feedback (attr-description @attr-feedback))
    after-feedback]])

(defn checkbox-field
  [{:donut.field/keys [tip required no-label? attr-feedback]
    :as opts}]
  [:div.field {:class (field-classes opts)}
   [:div
    (if no-label?
      [:span [input opts] [:i]]
      [:label {:class "donut-label"}
       [input opts]
       [:i]
       (label-text opts)
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

  [input (all-input-opts :form-key :text :user/username {:x :y})]"
  [all-input-opts-fn]
  (fn [input-type & [attr-path input-opts]]
    [field (if (map? input-type)
             (all-input-opts-fn (:donut.input/type input-type)
                                (:donut.input/attr-path input-type)
                                input-type)
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
  [form-key formwide-opts input-type attr-path & [opts]]
  (-> {:donut.input/form-key     form-key
       :donut.input/type         input-type
       :donut.input/attr-path    attr-path
       :donut.input/feedback-fns (:feedback-fns formwide-opts)}
      (merge (:donut.form/default-input-opts formwide-opts))
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

  `[input (all-input-opts :form-key :text :user/username {:x :y})]`"
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
  [form-key sync-key & [submit-opts]]
  (let [submit-opts (sugar-submit-opts submit-opts sync-key)]
    (when-not (:prevent-submit? submit-opts)
      (rf/dispatch [::dff/submit-form form-key submit-opts]))))

(defn form-sync-subs
  [sync-key]
  {:*sync-state    (rf/subscribe [::dff/sync-state sync-key])
   :*sync-active?  (rf/subscribe [::dff/sync-active? sync-key])
   :*sync-success? (rf/subscribe [::dff/sync-success? sync-key])
   :*sync-fail?    (rf/subscribe [::dff/sync-fail? sync-key])})

(defn form-subs
  [form-key & [{:keys [feedback-fns *sync-key]}]]
  (merge {:*form-ui-state (rf/subscribe [::dff/ui-state form-key])
          :*form-errors   (rf/subscribe [::dff/errors form-key])
          :*form-feedback (rf/subscribe [::dffk/form-feedback feedback-fns])
          :*form-buffer   (rf/subscribe [::dff/buffer form-key])
          :*form-dirty?   (rf/subscribe [::dff/form-dirty? form-key])}
         (form-sync-subs *sync-key)))

(defn form-components
  [form-key & [formwide-opts *sync-key]]
  (let [input-opts-fn (partial all-input-opts
                               form-key
                               formwide-opts)]
    {:*submit     (partial submit form-key *sync-key)
     :*input-opts input-opts-fn
     :*input      (input-component input-opts-fn)
     :*field      (field-component input-opts-fn)}))

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [form-key & [formwide-opts]]
  (merge (form-subs form-key formwide-opts)
         (form-components form-key formwide-opts)))
