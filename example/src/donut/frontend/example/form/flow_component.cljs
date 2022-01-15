(ns donut.frontend.example.form.flow-component
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.flow :as dff]
   [donut.frontend.sync.dispatch.echo :as dsde]
   [re-frame.core :as rf]))

(defn random-string
  []
  (subs (str (random-uuid)) 0 8))

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
               {:sync {::dsde/echo {:status        :success
                                    :response-data {:id   (rand-int 20)
                                                    :name (:username @*form-buffer)}}}
                :success [::dff/clear *form-path]})}])

(defn simple-form-example
  []
  (dfc/with-form [:post :users]
    [:form
     [:div
      [:h2 "simple form example"]
      [:div
       [:p "input helpers manage tracking state in the global app db"]
       [*input :text :username]
       [read-form-buffer *form-buffer]
       [:div [submit-button *form]]]
      [:div "users:"
       (->> @(rf/subscribe [::dcf/entities :user :id])
            (map (fn [u] [:li (str u)]))
            (into [:ul]))]]]))

(defn examples
  []
  [:div [:h1 "form examples"]
   [simple-form-example]])
