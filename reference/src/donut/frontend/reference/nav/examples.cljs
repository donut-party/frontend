(ns donut.frontend.reference.nav.examples
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.reference.ui :as ui]
   [donut.frontend.nav.flow :as dnf]
   [re-frame.core :as rf]))

(defn route-sub
  [sub-name]
  [:div
   [:div {:class "font-mono font-semibold mt-4"} (str sub-name)]
   [:div
    {:class "font-mono text-sm"}
    @(rf/subscribe [sub-name])]])

(defn route-subs
  []
  [:div
   [route-sub ::dnf/nav]
   [route-sub ::dnf/route]
   [route-sub ::dnf/params]
   [route-sub ::dnf/nav-state]
   [route-sub ::dnf/route-name]])

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
    [:div ":nav.flow-1 lifecycle fire count:"
     @(rf/subscribe [::dcf/get-in [::flow-1-val]])]
    [:div ":nav.flow-2 lifecycle fire count:"
     @(rf/subscribe [::dcf/get-in [::flow-2-val]])]]])

(defn links
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h2 "links and subscriptions"]
    [ui/explain "click these links to see how route sub values change"]
    [ui/example-offset
     [:div
      [:span {:class "mr-2"}
       [ui/route-link {:route-name :nav.flow} ":nav.flow"]]
      [:span {:class "mr-2"}
       [ui/route-link {:route-name :nav.flow-1}
        ":nav.flow-1"]]
      [:span {:class "mr-2"}
       [ui/route-link {:route-name :nav.flow-2}
        ":nav.flow-2"]]]

     [:div
      [route-sub ::dnf/nav]
      [route-sub ::dnf/route]
      [route-sub ::dnf/params]
      [route-sub ::dnf/nav-state]
      [route-sub ::dnf/route-name]]
     ]]])

(defn buffer
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h3 "buffer"]
    [:p "you can associate values with navigation so that they're cleared on navigation events"]
    [:div [:button {:on-click #(rf/dispatch [::dnf/assoc-in-buffer [:params] "foo"])} "populate buffer"]]
    [:div "buffer val: "
     @(rf/subscribe [::dnf/buffer])]]])

(defn prevent-nav-change
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/h3 "prevent nav change"]
    [ui/explain "you can use a lifecycle handler to prevent nav changes"]
    [ui/example-offset
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
    [ui/explain "the ::dnf/routed-entity subscription uses an id-key in route params to look up an entity"]
    [ui/example-offset
     [:div
      [ui/button
       {:on-click #(rf/dispatch [::dcf/merge-entity ::example :id {:id 1, :username "bobby"}])}
       "populate entity in [:entity ::example 1]"]]
     [:div [ui/route-link {:route-name   :nav.routed-entity
                           :route-params {:id 1}}
            ":nav.routed-entity"]]
     [:div "routed entity: " @(rf/subscribe [::dnf/routed-entity ::example :id])]]]])

(defn examples
  []
  [:div
   [ui/h1 "navigation"]
   [ui/explain "donut has a rich set of tools for handling navigation."]
   [links]
   [lifecycle-handlers]
   [buffer]
   [prevent-nav-change]
   [routed-entity-component]])
