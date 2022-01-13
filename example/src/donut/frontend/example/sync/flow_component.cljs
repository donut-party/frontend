(ns donut.frontend.example.sync.flow-component
  (:require [re-frame.core :as rf]
            [donut.frontend.sync.flow :as dsf]
            [donut.frontend.core.flow :as dcf]
            [donut.frontend.sync.dispatch.echo :as dsde]))


;; TODO list
;; - specify success handler
;; - return a vector of records
;; - return segments
;; - handle failure
;;   - show failure message

(defn success-example
  []
  [:div
   [:div
    [:button
     ;; TODO make name random
     {:on-click #(rf/dispatch [::dsf/get :users {::dsde/echo {:status        :success
                                                              :response-data {:id   1
                                                                              :name (str (random-uuid))}}}])}
     "click"]]
   [:div
    "entity at {:user 1}: "
    @(rf/subscribe [::dcf/entity :user 1])]])

(defn examples
  []
  [:div
   [:h2 "donut.frontend.sync.flow"]
   [success-example]])
