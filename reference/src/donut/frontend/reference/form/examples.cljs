(ns donut.frontend.reference.form.examples
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.form.feedback :as dffk]
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
  [*form-buffer attr-name]
  [:div
   [:div (str (get @*form-buffer attr-name))]])

;; Broken out into a separate component so that it can be updated with the
;; username without causing the *input component to re-render and thus lose
;; focus
(defn submit-button
  [{:keys [*submit *form-buffer *form-path]}]
  [:input
   {:type     "submit"
    :value    "submit"
    :on-click (dcu/prevent-default
               #(*submit
                 ;; we have to use echo here because we don't actually have a backend
                 {::dsde/echo {:status        :success
                               :response-data (assoc @*form-buffer :id (rand-int 1000))
                               :ms            1000}
                  ;; removes form data after success
                  :success [::dff/clear *form-path]}))}])

(defn submitting-indicator
  [*sync-active?]
  (when @*sync-active?
    [:span "submitting..."]))

(defn markdown [txt]
  {:dangerouslySetInnerHTML #js {:__html (marked (or txt ""))}})

(defn rendered-markdown
  [*form-buffer]
  [:div "rendered markdown:"
   [:div (markdown (:profile @*form-buffer))]])

(defn input-example-row
  [*form attr-name input-component]
  [:tr
   [:td (str attr-name)]
   [:td input-component]
   [:td [read-form-buffer (:*form-buffer *form) attr-name]]])

(defn form-example-features
  []
  (dfc/with-form [:post :users]
    [:form
     [:div
      [:div
       [:h2 "user form"]
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
        " sets the username to 'marcy'"]
       [:div
        [:p "input components manage state in the global app db:"]
        [:table
         [:tbody
          [input-example-row
           *form
           :username
           [:div
            [(dcu/focus-component
              [*input :text :username])]
            "(this automatically gains focus)"]]
          [input-example-row *form :active? [*input :checkbox :active? {:donut.input/value true}]]
          [input-example-row *form :remind-on [*input :date :remind-on]]
          [input-example-row *form :score [*input :number :score]]
          [input-example-row
           *form
           :email-preferences
           [:div
            [:div
             [*field :checkbox-set :email-preferences
              {:donut.field/label "gimme marketing emails, i am a weirdo"
               :donut.input/value :marketing}]
             [*field :checkbox-set :email-preferences
              {:donut.field/label "gimme service emails"
               :donut.input/value :service}]]]]
          [input-example-row
           *form
           :favorite-pet
           [*input :select :favorite-pet
            {:donut.input/select-options [[nil "Select one"]
                                          [:dozer "Dozer"]
                                          [:janie "Janie"]
                                          [:cloud "Cloud"]
                                          [:link "Link"]
                                          [:rory "Rory"]]}]]
          [input-example-row
           *form
           :dream-vacation
           [:div
            [:div
             [*field :radio :dream-vacation
              {:donut.field/label "mountains"
               :donut.input/value :mountains}]
             [*field :radio :dream-vacation
              {:donut.field/label "beach"
               :donut.input/value :beach}]]]]
          [:tr
           [:td ":profile"]
           [:td
            [:div
             [:p "you can create custom input elements, like this markdown editor"]
             [*input :simplemde :profile]]]
           [:td [rendered-markdown *form-buffer]]]]]]

       [:div
        [:p "with-form includes a helper function for submitting the form"]
        [submit-button *form]
        [submitting-indicator *sync-active?]
        [:span " <- a submitting indicator will show up here when you hit submit"]]]
      [:div
       [:h2 "submitted users"]
       (->> @(rf/subscribe [::dcf/entities :user :id])
            (map (fn [u] [:li (str u)]))
            (into [:ul]))]]]))

;;---
;; validation examples
;;---

(defn validation-example-stored-errors
  []
  [:div
   [:h2 "Validation Example 1: Server-side-validation"]
   [:p "This simulates a setup where your form fails server-side validation.
        It hides the field's error message when its input receives focus, because
        I think that's a friendlier design."]
   (dfc/with-form [:post :users]
     {:feedback-fns {:errors dffk/stored-error-feedback}}
     [:div
      [*field :text :first-name]
      [:input {:type     "submit"
               :value    "populate errors"
               :on-click #(*submit {::dsde/echo {:status        :fail
                                                 :response-data [[:errors {:attrs {[:first-name] ["bad username"]}}]]}})}]])])

(def UserSchema
  [:map
   [:zip-code {:error/message "required"} [:string {:min 5}]]])

(defn validation-example-dynamic
  []
  [:div
   [:h2 "Validation Example 2: Dynamic"]
   [:p "Uses malli to validate a form. Validation message doesn't appear until blur.
        Otherwise it'd be obnoxious, telling you the input is incorrect when you haven't
        even finished filling it out."]
   (dfc/with-form [:post :users]
     {:feedback-fns {:errors (dffk/malli-error-feedback-fn UserSchema)}}
     [:div
      [*field :text :zip-code]])])

(defn examples
  []
  [:div [:h1 "form examples"]
   [form-example-features]
   [validation-example-stored-errors]
   [validation-example-dynamic]])
