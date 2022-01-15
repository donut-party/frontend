(ns donut.frontend.reference.sync.examples
  (:require [re-frame.core :as rf]
            [donut.frontend.core.flow :as dcf]
            [donut.frontend.path :as p]
            [donut.frontend.sync.flow :as dsf]
            [donut.frontend.sync.dispatch.echo :as dsde]))


;; TODO list
;; - specify success handler
;; - handle failure
;;   - show failure message

(defn random-string
  []
  (subs (str (random-uuid)) 0 8))

(defn basic-success-example
  []
  [:div
   [:h3 "basic sync success"]
   [:div
    [:button
     ;; TODO make name random
     {:on-click #(rf/dispatch [::dsf/get :users {::dsde/echo {:status        :success
                                                              :response-data {:id   1
                                                                              :name (random-string)}}}])}
     "click"]]
   [:div
    "entity at [:user 1]: "
    @(rf/subscribe [::dcf/entity :user 1])]])

(defn multiple-entities-success-example
  []
  [:div
   [:h3 "sync success with vector response data"]
   [:div
    [:button
     ;; TODO make name random
     {:on-click #(rf/dispatch [::dsf/get :users {::dsde/echo {:status        :success
                                                              :response-data [{:id   2
                                                                               :name (random-string)}
                                                                              {:id   3
                                                                               :name (random-string)}]}}])}
     "click"]]
   [:div
    "entities at [:user]: "
    @(rf/subscribe [::dcf/get-in (p/path :entity [:user])])]])

(defn segments-example
  []
  [:div
   [:h3 "segments with entities example"]
   [:div
    [:button
     ;; TODO make name random
     {:on-click
      #(rf/dispatch [::dsf/get
                     :users
                     {::dsde/echo {:status        :success
                                   :response-data [[:entity [:post :id {:id      1
                                                                        :content (random-string)}]]
                                                   [:entities [:post :id [{:id      2
                                                                           :content (random-string)}
                                                                          {:id      3
                                                                           :content (random-string)}]]]]}}])}
     "click"]]
   [:div
    "entities at [:post]:"
    @(rf/subscribe [::dcf/get-in (p/path :entity [:post])])]])

(defn examples
  []
  [:div
   [:h2 "donut.frontend.sync.flow"]
   [basic-success-example]
   [multiple-entities-success-example]
   [segments-example]])
