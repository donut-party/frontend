(ns donut.frontend.reference.nav.examples
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.reference.ui :as ui]
   [donut.frontend.nav.flow :as dnf]
   [re-frame.core :as rf]))

;;---
;; subs and links
;;---

(defn route-sub
  [sub-name]
  [:div {:class "mt-4"}
   [:div {:class "bg-gray-200 px-2 py-1 mb-2 rounded-md"}
    "subscription value for "
    [:span {:class "font-mono font-semibold"} (str sub-name)]]
   [ui/pprint @(rf/subscribe [sub-name])]])

(defn route-subs
  []
  [:div
   [route-sub ::dnf/nav]
   [route-sub ::dnf/route]
   [route-sub ::dnf/params]
   [route-sub ::dnf/nav-state]
   [route-sub ::dnf/route-name]])

(defn nav-links
  []
  [:div {:class "font-semibold mb-3"}
   "route links: "
   [:span {:class "mr-2"}
    [ui/route-link {:route-name :nav.flow-1
                    :class "text-blue-800 hover:text-green-500 underline"}
     ":nav.flow-1"]]
   [:span {:class "mr-2"}
    [ui/route-link {:route-name :nav.flow-2
                    :class "text-blue-800 hover:text-green-500 underline"}
     ":nav.flow-2"]]])

(defn links
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "links and subscriptions"]
    [ui/explain
     "As you navigate an SPA, the route matching the current URL is stored in the global
      app db. donut includes subscriptions for this state."]
    [ui/explain
     "donut also includes helper components for creating links using route names."]
    [ui/example-offset
     [:div {:class "font-semibold"}
      "route links: "
      [:span {:class "mr-2"}
       [ui/route-link {:route-name :nav.flow
                       :class "text-blue-800 hover:text-green-500 underline"}
        ":nav.flow"]]
      [:span {:class "mr-2"}
       [ui/route-link {:route-name :nav.flow-1
                       :class "text-blue-800 hover:text-green-500 underline"}
        ":nav.flow-1"]]
      [:span {:class "mr-2"}
       [ui/route-link {:route-name :nav.flow-2
                       :class "text-blue-800 hover:text-green-500 underline"}
        ":nav.flow-2"]]]

     [:div
      [route-sub ::dnf/nav]
      [route-sub ::dnf/route]
      [route-sub ::dnf/params]
      [route-sub ::dnf/nav-state]
      [route-sub ::dnf/route-name]]]]])

;;---
;; lifecycle handlers
;;---

(rf/reg-event-db ::inc-flow-1-lifecycle-fire-count
  [rf/trim-v]
  (fn [db _]
    (update db ::flow-1-val (fnil inc 0))))

(rf/reg-event-db ::set-flow-2-val
  [rf/trim-v]
  (fn [db [val]]
    (assoc db ::flow-2-val val)))

(defn lifecycle-handlers
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "lifecycle handlers"]
    [ui/explain
     "Route definitions can include a ::dfe/on key that specifies lifecycle
      handlers. For example, you could create a handler to initialize a form on
      entering a route (navigating to it) and to clear the form on exiting the route
      (navigating to some other route.) The examples below demonstrate lifecycle
      handlers being dispatched when the corresponding routes are entered."]

    [ui/example-offset
     [nav-links]
     [:div ":nav.flow-1 lifecycle updates a count every time it fires: "
      @(rf/subscribe [::dcf/get-in [::flow-1-val]])]
     [:div ":nav.flow-2 lifecycle sets a random value: "
      @(rf/subscribe [::dcf/get-in [::flow-2-val]])]]]])

(defn buffer
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h3 "buffer"]
    [ui/explain
     "You can associate values with navigation so that they're cleared on
      navigation events. One use for this is setting some UI state, like showing
      a dropdown, that should get cleared if you navigate away from the page
      showing the dropdown. Use the button below to populate nav state, then
      navigate away to see it get cleared."]
    [ui/example-offset
     [nav-links]
     [:div {:class "my-3"}
      [ui/button {:on-click #(rf/dispatch [::dnf/assoc-in-buffer [:params] "foo"])} "populate buffer"]]
     [:div "buffer val: "
      @(rf/subscribe [::dnf/buffer])]]]])

(defn prevent-nav-change
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h3 "prevent nav change"]
    [ui/explain
     "You can use a lifecycle handler to check whether to prevent nav changes.
      You might do this if a person is filling out a form and hasn't saved it."]
    [ui/explain
     "To try this, toggle nav prevention with the button below and then try to
      navigate new a new route. This is just a demo; a real example would
      probably have a prompt like, Are you sure you want to leave?"]
    [ui/example-offset
     [nav-links]
     [ui/button
      {:on-click #(rf/dispatch [::dcf/update-in [::prevent-change] not])}
      "toggle nav change prevention"]
     " "
     [:span "preventing change? "
      (-> @(rf/subscribe [::dcf/get-in [::prevent-change]])
          boolean
          str)]]]])

(defn routed-entity-component
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h3 "routed entity"]
    [ui/explain
     "nav includes the notion of a \"routed entity\" - an entity whose indexed
     id corresponds to an id value in the route's params. If you have a user with id 1
     stored in the global state atom under [:users 1], and you navigate to /user/1, then
     the subscription [::dnf/routed-entity :users :id] will return the correct user. "]
    [ui/example-offset
     [:div
      [ui/button
       {:on-click #(rf/dispatch [::dcf/merge-entity ::example :id {:id 1, :username "bobby"}])}
       "populate entity in [:entity ::example 1]"]]
     [:div [ui/route-link {:route-name   :nav.flow.routed-entity
                           :route-params {:id 1}}
            ":nav.flow.routed-entity"]]
     [:div "routed entity: " @(rf/subscribe [::dnf/routed-entity ::example :id])]]]])

(defn examples
  []
  [:div
   [ui/h1 "navigation"]
   [ui/explain "donut has a rich set of tools for handling navigation."]
   [links]
   [lifecycle-handlers]
   #_
   [buffer]
   [prevent-nav-change]
   [routed-entity-component]])
