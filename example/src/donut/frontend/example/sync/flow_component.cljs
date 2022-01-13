(ns donut.frontend.example.sync.flow-component
  (:require [re-frame.core :as rf]
            [donut.frontend.sync.flow :as dsf]))

;; example req use cases

(comment
  ;; what gets encoded in a sync request?
  ;; - the data for the request
  ;;   - route data
  ;;   - form data
  ;; - callbacks / handlers
  ;; -

  (rf/dispatch
   [::dsf/post
    :stripe.sessions
    {:params    {}
     :callbacks {:success [::choose-subscription-success :$ctx]}}])

  (rf/dispatch
   [::dsf/post
    :stripe.sessions
    {:params      {}
     :callbacks   {:success [::choose-subscription-success :$ctx]}
     ::dsf/req-id :foo}])


  (rf/dispatch
   [::dsf/post
    :stripe.sessions
    {:params      {}
     :on          {:success [[::choose-subscription-success :$ctx]
                             [::foo :x]]}
     ::dsf/req-id :foo}])

  (rf/dispatch
   [::dsf/post
    :stripe.sessions
    {:params      {}
     :on          {:success [[::choose-subscription-success :$ctx]
                             [::foo :x]]}
     ::dsf/req-id :foo}])

  )

(defn success-example
  []
  [:div
   [:button
    {:on-click #(rf/dispatch [::dsf/get :users])}
    "click"]])

(defn examples
  []
  [:div
   [:h2 "donut.frontend.sync.flow"]
   [success-example]])
