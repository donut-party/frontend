(ns donut.frontend.example.nav.flow-component
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.nav.components :as dnc]
   [donut.frontend.nav.flow :as dnf]
   [re-frame.core :as rf]))

(defn route-sub
  [sub-name]
  [:div (str sub-name) " "
   @(rf/subscribe [sub-name])])

(defn route-subs
  []
  [:div
   [:h3 "route subs"]
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
  [:div
   [:h3 "lifecycle handlers"]
   [:div ":nav.flow-1 lifecycle fire count:"
    @(rf/subscribe [::dcf/get-in [::flow-1-val]])]
   [:div ":nav.flow-2 lifecycle fire count:"
    @(rf/subscribe [::dcf/get-in [::flow-2-val]])]])

(defn links
  []
  [:div
   [:h3 "links"]
   [:p "click these links to see how route sub values change"]
   [:ul
    [:li [dnc/route-link :nav.flow ":nav.flow"]]
    [:li [dnc/route-link :nav.flow-1 ":nav.flow-1"]]
    [:li [dnc/route-link :nav.flow-2 ":nav.flow-2"]]]])

(defn buffer
  []
  [:div
   [:h3 "buffer"]
   [:p "you can associate values with navigation so that they're cleared on navigation events"]
   [:div [:button {:on-click #(rf/dispatch [::dnf/assoc-in-buffer [:params] "foo"])} "populate buffer"]]
   [:div "buffer val: "
    @(rf/subscribe [::dnf/buffer])]])

(defn prevent-nav-change
  []
  [:div
   [:h3 "prevent nav change"]
   [:p "you can use a lifecycle handler to prevent nav changes"]
   [:div
    [:button
     {:on-click #(rf/dispatch [::dcf/update-in [::prevent-change] not])}
     "toggle nav change prevention"]
    " "
    [:span "preventing change? "
     (-> @(rf/subscribe [::dcf/get-in [::prevent-change]])
         boolean
         str)]]])

(defn routed-entity-component
  []
  [:div
   [:h3 "routed entity"]
   [:p "the ::dnf/routed-entity subscription uses an id-key in route params to look up an entity"]
   [:div
    [:button
     {:on-click #(rf/dispatch [::dcf/merge-entity ::example :id {:id 1, :username "bobby"}])}
     "populate entity in [:entity ::example 1]"]]
   [:div [dnc/route-link :nav.routed-entity {:id 1} ":nav.routed-entity"]]
   [:div "routed entity: " @(rf/subscribe [::dnf/routed-entity ::example :id])]])

(defn examples
  []
  [:div
   [links]
   [route-subs]
   [lifecycle-handlers]
   [buffer]
   [prevent-nav-change]
   [routed-entity-component]])
