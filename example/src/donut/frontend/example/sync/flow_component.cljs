(ns donut.frontend.example.sync.flow-component
  (:require [re-frame.core :as rf]
            [donut.frontend.sync.flow :as dsf]
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
   [:button
    {:on-click #(rf/dispatch [::dsf/get :users {::dsde/echo {:status        :success
                                                             :response-data {:id   1
                                                                             :name "bob"}}}])}
    "click"]])

(defn examples
  []
  [:div
   [:h2 "donut.frontend.sync.flow"]
   [success-example]])
