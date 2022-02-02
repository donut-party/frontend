(ns donut.frontend.reference.sync.examples
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.path :as p]
   [donut.frontend.reference.ui :as ui]
   [donut.frontend.sync.dispatch.echo :as dsde]
   [donut.frontend.sync.flow :as dsf]
   [re-frame.core :as rf]))


;; TODO list
;; - specify success handler
;; - handle failure
;;   - show failure message
;; - rules

(defn random-string
  []
  (subs (str (random-uuid)) 0 8))

(defn basic-success-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "basic sync success"]
    [ui/explain
     "By default, when an API call returns a map it's merged into the global
     state atom under [entity-type entity-id]. Both entity-type and entity-id
     are derived from metadata associated with the route for the API call."]
    [ui/example-offset
     [ui/button
      ;; TODO make name random
      {:on-click #(rf/dispatch [::dsf/get
                                :users
                                {::dsde/echo {:status        :success
                                              :response-data {:id   1
                                                              :name (random-string)}}}])}
      "click to simulate a map response"]
     [:div {:class "mt-3"}
      [:div "This subscription returns the entity at [:user 1]: "]
      [ui/pprint @(rf/subscribe [::dcf/entity :user 1])]]]]])

(defn multiple-entities-success-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "sync success with vector response data"]
    [ui/explain
     "By default, when an API call returns a a vector of maps, the maps are all
     merged into the global state atom under [entity-type entity-id] for each
     map. Both entity-type and entity-id are derived from metadata associated
     with the route for the API call."]
    [ui/example-offset
     [ui/button
      ;; TODO make name random
      {:on-click #(rf/dispatch [::dsf/get
                                :users
                                {::dsde/echo {:status        :success
                                              :response-data [{:id   2
                                                               :name (random-string)}
                                                              {:id   3
                                                               :name (random-string)}]}}])}
      "click to simulate a vector-of-maps response"]
     [:div {:class "mt-3"}
      [:div "entities at [:user]:"]
      [ui/pprint @(rf/subscribe [::dcf/get-in (p/path :entity [:user])])]]]]])

(defn segments-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "segments (with entities)"]
    [ui/explain
     "What if you want to return data other than entities? donut has an
      extensible mechanism for processing arbitrary-ish data from an API call. A
      response can include a vector of \"segments\", where each segment is a
      2-vector of [:segment-type segment-payload]. Segments are processed with a
      multimethod, so you can introduce custom segment types with custom behavior."]
    [ui/explain
     "The example below shows two built-in segment types, :entity and :entities.
     The behavior of these segments is similar to the behavior described in the
     examples above."]
    [ui/explain
     "These segments types can be useful if you want to return entities of
     different types, or entities whose types differ from that in the endpoint
     routes."]
    [ui/explain
     "Segment extensibility means that you can easily introduce, say a :graphql
     segment type."]
    [ui/example-offset
     [ui/button
      ;; TODO make name random
      {:on-click
       #(rf/dispatch [::dsf/get
                      :users
                      {::dsde/echo {:status        :success
                                    :response-data [[:entities [:post :id [{:id      1
                                                                            :content (random-string)}]]]
                                                    [:entities [:post :id [{:id      2
                                                                            :content (random-string)}
                                                                           {:id      3
                                                                            :content (random-string)}]]]]}}])}
      "click to simulate a segments response"]
     [:div {:class "mt-3"}
      [:div "entities at [:post]:"]
      [ui/pprint @(rf/subscribe [::dcf/get-in (p/path :entity [:post])])]]]]])

(defn sync-status-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "sync state subscription"]
    [ui/explain
     "Sync requests are stored in the global state atom, and their state is
     updated over the lifetime of a request. You can subscribe to this."]

    [ui/example-offset
     [ui/button
      ;; TODO make name random
      {:on-click #(rf/dispatch [::dsf/get :users {::dsde/echo {:status :success
                                                               :ms     1000}}])}
      "click to simulate a successful sync request that takes 1 second"]
     [:div {:class "mt-3"}
      "Sync state: " @(rf/subscribe [::dsf/sync-state [:get :users]])]]]])

(defn examples
  []
  [:div
   [ui/h1 "donut.frontend.sync.flow"]
   [basic-success-example]
   [multiple-entities-success-example]
   [segments-example]
   [sync-status-example]])
