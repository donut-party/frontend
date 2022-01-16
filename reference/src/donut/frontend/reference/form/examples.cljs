(ns donut.frontend.reference.form.examples
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.reference.form.simplemde]
   [donut.frontend.sync.dispatch.echo :as dsde]
   ["marked" :as marked]
   [re-frame.core :as rf]))

(defn random-string
  []
  (subs (str (random-uuid)) 0 8))

;;---
;; common case features
;;---

(defn read-form-buffer
  [*form-buffer]
  [:div
   [:div "you can easily access the stored value associated with an input:"]
   [:div (get @*form-buffer :example-attr)]
   [:div "(this is a separate component so that its rendering doesn't interfere with the input)"]])

;; Broken out into a separate component so that it can be updated with the
;; username without causing the *input component to re-render and thus lose
;; focus
(defn submit-button
  [{:keys [*submit-fn *form-buffer *form-path]}]
  [:input
   {:type     "submit"
    :value    "submit"
    :on-click (*submit-fn
               ;; we have to use echo here because we don't actually have a backend
               {:sync    {::dsde/echo {:status        :success
                                       :response-data {:id   (rand-int 1000)
                                                       :name (:username @*form-buffer)}
                                       :ms            1000}}
                ;; removes form data after success
                :success [::dff/clear *form-path]})}])

(defn submitting-indicator
  [*sync-active?]
  (when @*sync-active?
    [:span "submitting..."]))

(defn form-example-common-case-features
  []
  (dfc/with-form [:post :users]
    [:form
     [:div
      [:h2 "form example with common-case features"]
      [:div
       [:div
        [:button {:on-click
                  ;; use prevent-default here to prevent the enclosing form from
                  ;; submitting.
                  ;; another option is to just not have an enclosing form
                  (dcu/prevent-default
                   #(rf/dispatch [::dff/initialize-form
                                  *form-path
                                  {:buffer {:username "marcy"}}]))}
         "populate form"]
        " sets the input value to 'marcy'"]
       [:p "input components manage tracking state in the global app db"]
       [(dcu/focus-component
         [*input :text :username])]
       [read-form-buffer *form-buffer]
       [:div
        [submit-button *form]
        [submitting-indicator *sync-active?]]]
      [:div "users:"
       (->> @(rf/subscribe [::dcf/entities :user :id])
            (map (fn [u] [:li (str u)]))
            (into [:ul]))]]]))

;;---
;; more features
;;---

(defn markdown [txt]
  {:dangerouslySetInnerHTML #js {:__html (marked (or txt ""))}})

(defn rendered-markdown
  [*form-buffer]
  [:div "rendered markdown:"
   [:div (markdown (:profile @*form-buffer))]])

(defn form-example-more-features
  []
  (dfc/with-form [:put :user {:id 1}]
    [:form
     [:div
      [:h2 "form example with less-common but still useful features"]
      [:div
       [:p "you can create your own custom input elements, like this markdown editor:"]
       [*input :simplemde :profile]
       [rendered-markdown *form-buffer]]]]))

(defn examples
  []
  [:div [:h1 "form examples"]
   [form-example-common-case-features]
   [form-example-more-features]])
