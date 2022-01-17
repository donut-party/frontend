(ns donut.frontend.form.feedback
  "status: very alpha

  Crucial to form UX is providing good feedback. Doing this can be challenging
  because there are many different approaches you can take: server side
  validation, client-side validation, or a mix of the two. Feedback is also not
  limited to showing errors; sometimes you want to give feedback that a field is
  correct. Finally, sometimes you want feedback to determine what actions can be
  taken next, for example by preventing form submission for an invalid form.

  Donut tries to help by creating an interface for this feedback, and by
  providing some implementations of that interface.

  The interface consists of:

  - a \"multi-arity\" subscription
    - the subscription's identifier is passed to a form as `:feedback-sub`, e.g.
      (with-form [:post :users] {:feedback-sub ::dffk/stored-errors}
    - the subscription's arities are:
      - `[partial-form-path]`: should return form-wide feedback
      - `[partial-form-path attr-path]` should return feedback for specified attr
  - feedback return value. this should be a map.
    - donut's `field` component relies on the convention of returning
      `{:errors [\"error one\" \"error-two\"]}` for displaying error messages.
    - for form-wide feedback, the convention is to use `:prevent-submit?` and
      `:submit-prevented?` keys. `:prevent-submit` should be used to control
      whether a form can be submitted. `:submit-prevented?` can be used to
      provide additional feedback if a user tries to submit a form, but isn't
      allowed to yet. This can provide additional instructions.
  - event tracking. donut form components by default record events they've
    received. This can be used to, for example, only show an error message
    if an input has received a `:blur` event.
  "

  (:require
   [clojure.set :as set]
   [donut.frontend.form.flow :as dff]
   [donut.sugar.utils :as dsu]
   [medley.core :as medley]
   [re-frame.core :as rf]
   [sweet-tooth.describe :as d]))

;; A simple subscription that simply returns
(rf/reg-sub ::stored-errors
  (fn [[_ partial-form-path attr-path]]
    (if attr-path
      (rf/subscribe [::dff/attr-errors partial-form-path attr-path])
      (rf/subscribe [::dff/errors partial-form-path])))
  (fn [errors _]
    {:errors errors}))

(defn received-events?
  [input-events pred-events]
  (seq (set/intersection input-events pred-events)))
