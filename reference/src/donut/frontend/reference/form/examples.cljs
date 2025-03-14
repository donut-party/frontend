(ns donut.frontend.reference.form.examples
  (:require
   ["marked" :as marked]
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.feedback :as dffk]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.reference.form.simplemde]
   [donut.frontend.reference.ui :as ui]
   [donut.frontend.sync.dispatch.echo :as dsde]
   [donut.frontend.sync.flow :as dsf]
   [re-frame.core :as rf]))

(defn random-string
  []
  (subs (str (random-uuid)) 0 8))

;;---
;; common case features
;;---

(defn read-form-buffer
  [*form-buffer attr-name]
  [:div
   [:div (str (get @*form-buffer attr-name))]])

;; Broken out into a separate component so that it can be updated with the
;; username without causing the *input component to re-render and thus lose
;; focus
(defn submit-button
  [{:keys [*submit *form-buffer *form-key]}]
  [ui/button
   {:on-click (dcu/prevent-default
               #(*submit
                 ;; we have to use echo here because we don't actually have a backend
                 {::dsde/echo {:status        :success
                               :response-data (assoc @*form-buffer :id (rand-int 1000))
                               :ms            2000}
                  :on         {:success [::dff/clear *form-key]}}))}
   "submit"])

(defn submitting-indicator
  [*sync-active?]
  (when @*sync-active?
    [:span "submitting..."]))

(defn markdown [txt]
  {:dangerouslySetInnerHTML #js {:__html (marked (or txt ""))}})

(defn rendered-markdown
  [*form-buffer]
  [:div "markdown -> html:"
   [:div (marked (or (:profile @*form-buffer) ""))]])

(defn input-example-row
  [*form attr-name input-component]
  [:div {:class "sm:grid sm:grid-cols-3 sm:gap-4 py-2"}
   [:div (str attr-name)]
   [:div input-component]
   [:div [read-form-buffer (:*form-buffer *form) attr-name]]])

(def input-class
  "shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full
   border sm:text-sm border-gray-300 rounded-md px-3 py-2")

(def checkbox-class
  "focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounde border mr-1")

(defn most-basic-form-value
  [*form-buffer]
  [:div {:class "mt-4"}
   [ui/h2 "Input value"]
   [:div (:form-attribute-name-goes-here @*form-buffer)]])

(defn most-basic-form
  []
  (dfc/with-form [:most-basic-form]
    [ui/example
     [:div {:class "p-4"}
      [:form
       [:div
        [:div
         [ui/h2 "A simple text field"]
         [*input :text :form-attribute-name-goes-here {:class input-class}]]
        [most-basic-form-value *form-buffer]]]]]))

(defn form-example-features
  []
  (dfc/with-form [:post :users]
    {:donut.form.layout/buffer [:test-buffer]}
    [ui/example
     [:div {:class "p-4"}
      [:form
       [:div
        [:div
         [ui/h2 "Built-in input types and custom inputs demo"]
         [ui/explain
          [:div
           "donut has a full-featured system for working with forms. Some of its features include:"
           [:ul {:class "list-disc ml-6"}
            [:li "storing form data in the global state atom"]
            [:li "a set schema for form data, one less decision for you to have to make"]
            [:li "wiring up input events so that you don't have to"]
            [:li "handling form submission lifecycle"]
            [:li "extensibility: you can easily create your own custom input types, like the markdown editor below"]
            [:li "feedback - not just validation, but confirmation that values are good"]]]]

         [ui/h3 "Fill in form data"]
         [ui/explain "There's an event to set form data"]
         [:div
          [ui/button
           {:on-click
            ;; use prevent-default here to prevent the enclosing form from
            ;; submitting.
            ;; another option is to just not have an enclosing form
            (dcu/prevent-default
             #(rf/dispatch [::dff/set-form
                            *form-config
                            {:buffer {:username "marcy"}}]))}
           "populate form"]
          " sets the username value to 'marcy'"]
         [ui/explain "input components manage state in the global app db:"]
         [:div {:class "sm:divide-y sm:divide-gray-200"}
          [input-example-row
           *form
           :username
           [:div
            [:div
             [*input :text :username {:class input-class :ref (dcu/focus-node-fn)}]]
            [:div "(this automatically gains focus)"]]]
          [input-example-row *form :email
           [*input :email :email {:class input-class}]]
          [input-example-row *form :active?
           [*input :checkbox :active?
            {:donut.input/value true
             :class checkbox-class}]]
          [input-example-row *form :remind-on
           [*input :date :remind-on {:class input-class}]]
          [input-example-row *form :score
           [*input :number :score {:class input-class}]]
          [input-example-row
           *form
           :email-preferences
           [:div
            [:div
             [*field :checkbox-set :email-preferences
              {:donut.field/label "gimme marketing emails, i am a weirdo"
               :donut.input/value :marketing
               :donut.input/class checkbox-class}]
             [*field :checkbox-set :email-preferences
              {:donut.field/label "gimme service emails"
               :donut.input/value :service
               :donut.input/class checkbox-class}]]]]
          [input-example-row
           *form
           :favorite-pet
           [*input :select :favorite-pet
            {:donut.input/select-options [[nil "Select one"]
                                          [:dozer "Dozer"]
                                          [:janie "Janie"]
                                          [:cloud "Cloud"]
                                          [:link "Link"]
                                          [:rory "Rory"]]
             :class input-class}]]
          [input-example-row
           *form
           :dream-vacation
           [:div
            [:div
             [*field :radio :dream-vacation
              {:donut.field/label "mountains"
               :donut.input/value :mountains
               :class checkbox-class}]
             [*field :radio :dream-vacation
              {:donut.field/label "beach"
               :donut.input/value :beach
               :class checkbox-class}]]]]

          [:div
           [:div ":profile"]
           [:div
            [ui/explain "you can create custom input elements, like this markdown editor"]
            [*input :simplemde :profile]]
           [:div
            [ui/example-offset
             [rendered-markdown *form-buffer]]]]]]]]]]))

;;---
;; activity example
;;---

(defn activity-example
  []
  (dfc/with-form [:post :users]
    [ui/example
     [:div {:class "p-4"}
      [ui/explain
       [:div
        [ui/h2 "Form submission state"]
        "The form library provides submit helpers that are tied to syncing.
            You can use a sync subscription to show feedback for form submission
            progress."]]
      [ui/explain
       [:div
        [submit-button *form]
        [submitting-indicator *sync-active?]
        [:span " <- a submitting indicator will show up here when you hit submit"]]]
      [:div
       [ui/h2 "submitted users"]
       [ui/example-offset
        [ui/pprint @(rf/subscribe [::dcf/entities :user :id])]]]]]))

;;---
;; validation examples
;;---

(defn validation-example-stored-errors
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "Validation Example 1: Server-side-validation"]
    [ui/explain
     "This simulates a setup where your form fails server-side validation.
        It hides the field's error message when its input receives focus, because
           I think that's a friendlier design."]
    (dfc/with-form [:post :users]
      {:donut.form/feedback-fn dffk/stored-error-feedback}
      [:div
       [*field :text :first-name {:class input-class}]
       [ui/button
        {:on-click #(*submit {::dsde/echo {:status        :fail
                                           :response-data [[:errors {:attrs {[:first-name] ["bad first name"]}}]]}})}
        "populate errors"]])]])

(def UserSchema
  [:map
   [:zip-code {:error/message "required"} [:string {:min 5}]]])

(defn validation-example-dynamic
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "Validation Example 2: Dynamic"]
    [ui/explain
     "Uses malli to validate a form. Validation message doesn't appear until blur.
        Otherwise it'd be obnoxious, telling you the input is incorrect when you haven't
        even finished filling it out."]
    (dfc/with-form [:post :users]
      {:donut.form/feedback-fn (dffk/malli-error-feedback-fn UserSchema)}
      [:div
       [*field :text :zip-code {:class input-class}]])]])

(defn sync-and-init-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "Populate form with sync response"]
    (dfc/with-form [:post :posts]
      [:div
       [ui/explain
        "You can perform a sync request and use the result to populate a form"]
       [ui/explain
        [ui/button
         {:on-click
          #(rf/dispatch [::dsf/get
                         :post
                         {:id         1
                          ::dsde/echo {:status        :success
                                       :response-data {:id      1
                                                       :content "post content"}}
                          :on         {:success [::dff/set-form-from-sync :$ctx *form-layout]}}])}
         "populate from sync"]]
       [*field :text :content {:class input-class}]])]])

(defn custom-input-class
  []
  ;; Alternatives for specifying classes
  [ui/example
   (dfc/with-form [:post :address]
     {:donut.form/feedback-fn (dffk/malli-error-feedback-fn UserSchema)}
     [:div {:class "p-4"}
      [ui/h2 "Custom input classes"]
      [ui/explain "You can customize the input class with feedback"]
      [ui/explain
       [*input :text :zip-code
        {:donut.input/class #(str input-class " " (dfc/feedback-classes %))}]]
      [ui/explain
       [*input :text :zip-code
        {:donut.input/class #(str input-class
                                  " "
                                  (dfc/feedback-classes % {:errors "border-red-500"}))}]]])])

(rf/reg-sub ::custom-buffer
  (fn [db _]
    (::custom-buffer db)))

(defn prevent-input-focus-loss
  "TODO I still don't fully understand why this is needed"
  []
  [ui/example
   (dfc/with-form [:post :address]
     {:donut.form.layout/buffer [::custom-buffer]}
     [:div {:class "p-4"}
      [ui/h2 "Inputs won't lose focus"]
      [ui/explain
       [:div "When we deref in a way that causes the input components to get
       recreated, they shouldn't lose focus"]]
      [ui/explain
       [:div "custom buffer:" @(rf/subscribe [::custom-buffer])]]
      [ui/explain
       [*input :text :blah {:class input-class}]]])])

(defn initial-values-submit-button
  [{:keys [*submit *form-buffer *form-key]}]
  [ui/button
   {:on-click (dcu/prevent-default
               #(*submit
                 ;; we have to use echo here because we don't actually have a backend
                 {::dsde/echo {:status        :success
                               :response-data (assoc @*form-buffer :id (rand-int 1000))
                               :ms            0}
                  :on         {:success [[::dff/clear *form-key]
                                         [::initial-values-success :$ctx]]}}))}
   "submit"])

(rf/reg-event-db ::initial-values-success
  [rf/trim-v]
  (fn [db args]
    (prn "args" args)
    db))

(defn initial-values
  []
  [ui/example
   (dfc/with-form [:post :users]
     {:donut.form/initial-state {:buffer {:initial-value-test "initial value"
                                          :nav-params @(rf/subscribe [::dnf/route])}}}
     [:div {:class "p-4"}
      [ui/h2 "Provide initial values for the form"]
      [ui/explain
       [:div "If you're editing something you want the form to have initial
       values for the thing edited. Or you might want hidden values in the
       form."]]
      [ui/explain
       [:div "In this example, the form has initial buffer values set for the
       ':initial-value-test' and ':nav-params' keys. The value of ':nav-params'
       is a subscription."]]
      [ui/explain
       [*field :text :initial-value-test {:class input-class}]]
      [:div
       [ui/h2 "submitted users"]
       [ui/example-offset
        [ui/pprint @(rf/subscribe [::dcf/entities :user :id])]]]
      [initial-values-submit-button *form]])])

(defn examples
  []
  [:div
   [ui/h1 "form examples"]
   [most-basic-form]
   [form-example-features]
   [activity-example]
   [validation-example-stored-errors]
   [validation-example-dynamic]
   [sync-and-init-example]
   [custom-input-class]
   [prevent-input-focus-loss]
   [initial-values]])
