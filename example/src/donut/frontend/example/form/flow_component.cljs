(ns donut.frontend.example.form.flow-component
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.flow :as dff]
   [re-frame.core :as rf]))

(defn form-post-example
  []
  (dfc/with-form [:post :form-example]
    [:div
     [:h2 "form post example"]
     [*input :text :test]]))

(defn examples
  []
  [:div [:h1 "form examples"]
   [form-post-example]])
