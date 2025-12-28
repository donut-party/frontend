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

  - a `::feedback` subscription. It takes a signle argument, `feedback-fn`,
    which takes a form as an argument and returns data in the shape described below
  - a `::form-feedback` subscription that takes a `form-layout` and
    `feedback-fn` to produce form-wide feedback. can be used to e.g. prevent
    form submission
  - an `::attr-feedback` subscription that produces feedback for a specific attr."
  (:require
   [clojure.set :as set]
   [donut.sugar.utils :as dsu]
   [malli.core :as m]
   [malli.error :as me]
   [medley.core :as medley]))

;;--------------------
;; specs
;;--------------------

(def FeedbackType
  [:enum
   :donut.feedback/error
   :donut.feedback/warn
   :donut.feedback/info
   :donut.feedback/ok])

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
  {:donut.feedback/warn  {:form :submission-not-allowed}
   :donut.feedback/error {:form  :submission-failed
                          :attrs {:username [:attr-msg-1 :attr-msg-2]
                                  :address  {:city   [:attr-msg-1 :attr-msg-2]
                                             :street [:attr-msg-1 :attr-msg-2]}}}
   :donut.feedback/info  {:form  :can-submit!
                          :attrs {:username [:attr-msg-1 :attr-msg-2]
                                  :address  {:city   [:attr-msg-1 :attr-msg-2]
                                             :street [:attr-msg-1 :attr-msg-2]}}}})

(defn received-events?
  [input-events pred-events]
  (seq (set/intersection input-events pred-events)))

(defn stored-error-feedback
  "Shows errors for attrs when they haven't received focus"
  [{:keys [feedback input-events]}]
  {:errors
   {:attrs (medley/filter-keys (fn [attr-path]
                                 (not (received-events? (get input-events (dsu/vectorize attr-path))
                                                        #{:focus})))
                               (get-in feedback [:errors :attrs]))
    :form  (get-in feedback [:errors :form])}})

;;---
;; malli feedback
;;---

(defn feedback-humanize
  "Humanized a explanation. Accepts the following optitons:
  - `:wrap`, a function of `error -> message`, defaulting to `:message`
  - `:resolve`, a function of `explanation error options -> path message`"
  ([explanation]
   (feedback-humanize explanation nil))
  ([{:keys [_value errors] :as explanation} {:keys [wrap resolve]
                                             :or   {wrap    :message
                                                    resolve me/-resolve-direct-error}
                                             :as   options}]
   (when errors
     (reduce
      (fn [acc error]
        (let [[path message] (resolve explanation error options)]
          (assoc acc path (wrap (assoc error :message message)))))
      nil errors))))

(defn malli-error-feedback-fn
  [schema & [error-overrides]]
  (fn [{:keys [buffer input-events]}]
    {:donut.feedback/error
     {:attrs (->> (-> schema
                      (m/explain (or buffer {}))
                      (feedback-humanize {:errors (merge me/default-errors
                                                         {::m/missing-key {:error/message "required"}}
                                                         error-overrides)
                                          :wrap   (comp vector :message)}))
                  (medley/filter-keys (fn [attr-path]
                                        (received-events? (get input-events (dsu/vectorize attr-path))
                                                          #{:blur}))))}}))

(defn merge-feedback-fns
  [& fns]
  (fn [form]
    (apply dsu/deep-merge (map #(% form) fns))))

(defn has-feedback?
  [feedback k]
  (let [{:keys [attrs form]} (get feedback k)]
    (or (seq form)
        (some seq (vals attrs)))))
