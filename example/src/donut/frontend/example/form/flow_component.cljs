(ns donut.frontend.example.form.flow-component
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.flow :as dff]
   [re-frame.core :as rf]))

(defn read-form-buffer
  [*form-buffer]
  [:div
   [:div "you can easily access the stored value associated with an input:"]
   [:div (get @*form-buffer :example-attr)]
   [:div "(this is a separate component so that its rendering doesn't interfere with the input)"]])

(defn form-example
  []
  (dfc/with-form [:post :form-example]
    [:div
     [:h2 "form example"]
     [:div
      [:p "input helpers manage tracking state in the global app db"]
      [*input :text :example-attr]
      [read-form-buffer *form-buffer]]]))

(defn examples
  []
  [:div [:h1 "form examples"]
   [form-example]])
