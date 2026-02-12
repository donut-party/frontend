(ns donut.frontend.form.components.field
  (:require
   [donut.compose :as dc]
   [donut.frontend.form.components.input :as input]
   [donut.sugar.utils :as dsu]))

(defn field-label-text [{:keys [:donut.input/attr-path]}]
  (dsu/kw-title (input/attr-path-str attr-path)))

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
  [{:donut.field/keys [feedback-class-mapping]
    :donut.input/keys [attr-feedback]}]
  (let [mapping (dc/compose css-classes feedback-class-mapping)]
    (->> @attr-feedback
         (remove #(empty? (second %)))
         (map first)
         (mapcat #(get mapping % (dsu/full-name %)))
         vec)))

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
      (conj (str "donut-field-for-" (dsu/kebab (input/attr-path-str attr-path))))))

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
           (composable :donut.field/label-opts {:for (input/label-for opts) :class (:donut.field/label-class css-classes)})
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
      [input/input opts]
      after-input
      [feedback-messages-component composable @attr-feedback]
      after-feedback]]))

(defn checkbox-field
  [{:donut.field/keys [tip required no-label?]
    :donut.input/keys [attr-feedback]
    :as opts}]
  (let [composable (dc/composable opts)]
    [:div (composable :donut.field/field-wrapper-opts {:class (field-wrapper-classes opts)})
     [:div (composable :donut.field/label-wrapper-opts {:class (:donut.field/label-wrapper-class css-classes)})
      (if no-label?
        [:span [input/input opts] [:i]]
        [:label (composable :donut.field/label-opts {:for (input/label-for opts) :class (:donut.field/label-class css-classes)})
         [input/input opts]
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
    [field (input/all-input-opts form-config field-opts)]))
