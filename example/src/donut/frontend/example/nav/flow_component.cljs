(ns donut.frontend.example.nav.flow-component
  (:require
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

(defn links
  []
  [:div
   [:h3 "links"]
   [:p "click these links to see how route sub values change"]
   [:ul
    [:li [dnc/route-link :nav.flow ":nav.flow"]]
    [:li [dnc/route-link :nav.flow-1 ":nav.flow-1"]]
    [:li [dnc/route-link :nav.flow-2 ":nav.flow-2"]]]])

(defn examples
  []
  [:div
   [links]
   [route-subs]])
