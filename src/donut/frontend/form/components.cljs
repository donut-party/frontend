(ns donut.frontend.form.components
  "- input just the input element
  - field input element with label and wrapper
  - form subscriptions
  - form system"
  (:require
   [clojure.set :as set]
   [donut.compose :as dc]
   [donut.frontend.form.components.input :as dfci]
   [donut.frontend.form.components.field :as dfcf]
   [donut.frontend.form.feedback :as dffk]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.sync.flow :as dsf]
   [re-frame.core :as rf])
  (:require-macros [donut.frontend.form.components]))

;;---
;; interface fns
;;---

(defn submit-when-ready
  [on-submit-handler form-feedback]
  (fn [e]
    (if (:prevent-submit? @form-feedback)
      (.preventDefault e)
      (on-submit-handler e))))

(defn sync-form
  [form-config & [sync-opts]]
  (when-not (:donut.form/prevent-submit? sync-opts)
    (rf/dispatch [::dff/sync-form (assoc form-config ::dff/sync-event sync-opts)])))

(defn form-config
  [{:keys [::dff/form-key] :as f-config}]
  (dc/compose {::dff/sync?       true
               ::dff/feedback-fn dffk/stored-error-feedback
               ::dff/sync-event  {::dsf/sync-key form-key}}
              f-config))

(defn form-sync-subs
  [form-config]
  (set/rename-keys (dsf/sync-subs (::dff/sync-event form-config))
                   {:sync-state    :*sync-state
                    :sync-active?  :*sync-active?
                    :sync-success? :*sync-success?
                    :sync-fail?    :*sync-fail?}))

(defn form-subs
  [{:keys [::dff/sync?] :as form-config}]
  (cond->  {:*form-ui-state (rf/subscribe [::dff/ui-state form-config])
            :*form-feedback (rf/subscribe [::dff/form-feedback form-config])
            :*form-buffer   (rf/subscribe [::dff/buffer form-config])
            :*form-dirty?   (rf/subscribe [::dff/form-dirty? form-config])}
    sync? (merge (form-sync-subs form-config))))

(defn attr-buffer
  [form-config attr-path]
  (rf/subscribe [::dff/attr-buffer (assoc form-config :donut.input/attr-path attr-path)]))

(defn form-components
  [form-config]
  {:*sync-form     (partial sync-form form-config)
   :*input         (dfci/input-component form-config)
   :*input-opts    (partial dfci/all-input-opts form-config)
   :*input-builder (dfci/input-builder form-config)
   :*field         (dfcf/field-component form-config)
   :*attr-buffer   (fn *attr-buffer [attr-path] (attr-buffer form-config attr-path))})

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [form-config]
  (merge (form-subs form-config)
         (form-components form-config)))
