(ns donut.frontend.form.feedback
  "status: very alpha

  Crucial to form UX is providing good feedback. Doing this can be challenging
  because there are many different approaches you can take: server side
  validation, client-side validation, or a mix of the two. Feedback is also not
  limited to showing errors; sometimes you want to give feedback that a field is
  correct. Finally, sometimes you want feedback to determine what actions can be
  taken next, for example by preventing form submission for an invalid form.

  Donut tries to help by introducing an interface for this feedback, and by
  providing some implementations of that interface.

  The interface consists of:

  - a `::feedback` subscription. It takes a signle argument, `feedback-fns`,
    which takes a form as an argument and returns data in the same described below
  - a `::form-feedback` subscription that takes a `partial-form-path` and
    `feedback-fns` to produce form-wide feedback. can be used to e.g. prevent
    form submission
  - an `::attr-feedback` subscription that produces feedback for a specific attr."
  (:require
   [clojure.set :as set]
   [donut.frontend.form.flow :as dff]
   [donut.sugar.utils :as dsu]
   [re-frame.core :as rf]))

(def FeedbackType
  [:enum :error :warn :info :ok])
(def FeedbackMap
  [:map
   [:form {:optional true} [:sequential any?]
    ;; attrs can actually be arbitrarily nested maps. i'm being
    ;; lazy about this right now
    :attr {:optional true} any?]])

(def Feedback
  [:map-of FeedbackType FeedbackMap])

(comment
  ;; feedback examples. top-level keys (:error, :info) are the feedback types
  {:error {:form  :prevent-submit
           :attrs {:username [:attr-msg-1 :attr-msg-2]
                   :address  {:city   [:attr-msg-1 :attr-msg-2]
                              :street [:attr-msg-1 :attr-msg-2]}}}
   :info  {:form  :can-submit!
           :attrs {:username [:attr-msg-1 :attr-msg-2]
                   :address  {:city   [:attr-msg-1 :attr-msg-2]
                              :street [:attr-msg-1 :attr-msg-2]}}}})

(defn received-events?
  [input-events pred-events]
  (seq (set/intersection input-events pred-events)))

(rf/reg-sub ::feedback
  (fn [[_ partial-form-path]]
    (rf/subscribe [::dff/form partial-form-path]))
  (fn [form [_ _ feedback-fns]]
    (reduce-kv (fn [feedback feedback-type feedback-fn]
                 (assoc feedback feedback-type (feedback-fn form)))
               {}
               feedback-fns)))

;; yields values of the form
;; {:error [e1 e2 e3]
;;  :info  [i1 i2 i3]
(rf/reg-sub ::form-feedback
  (fn [[partial-form-path feedback-fns]]
    (rf/subscribe [::feedback partial-form-path feedback-fns]))
  (fn [feedback]
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
  (fn [[_ partial-form-path _attr-path feedback-fns]]
    (rf/subscribe [::feedback partial-form-path feedback-fns]))
  (fn [feedback [_ _partial-form-path attr-path]]
    (reduce-kv (fn [attr-feedback feedback-type all-feedback]
                 (if-let [feedback (get (:attrs all-feedback)
                                        (dsu/vectorize attr-path))]
                   (assoc attr-feedback feedback-type feedback)
                   attr-feedback))
               {}
               feedback)))

(defn stored-error-feedback
  "Shows errors for attrs when they haven't received focus"
  [{:keys [errors input-events]}]
  {:attrs (reduce-kv (fn [visible-errors attr-path attr-input-events]
                       (if (contains? attr-input-events "focus")
                         (assoc visible-errors attr-path nil)
                         visible-errors))
                     (:attrs errors)
                     input-events)
   :form (:form errors)})
