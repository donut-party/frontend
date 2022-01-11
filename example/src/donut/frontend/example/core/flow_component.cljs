(ns donut.frontend.example.core-flow.component
  (:require [re-frame.core :as rf]
            [donut.frontend.core.flow :as dcf]))

;;---
;; debounce dispatch
;;---

(rf/reg-event-fx ::debounce-dispatch-example
  [rf/trim-v]
  (fn [_ _]
    {::dcf/debounce-dispatch
     [{:ms       1000
       :id       :debounce-example
       :dispatch [::debounce-dispatch-update-val]}]}))

(rf/reg-event-db ::debounce-dispatch-update-val
  [rf/trim-v]
  (fn [db]
    (update-in db [:examples ::dcf/debounce-dispatch] (fnil inc 0))))

(rf/reg-sub ::debounce-dispatch-example-val
  (fn [db]
    (get-in db [:examples ::dcf/debounce-dispatch])))

(defn debounce-dispatch-example
  []
  [:div.example
   [:h3 [:span.rf-name "::dcf/debounce-dispatch"]]
   [:button
    {:on-click #(rf/dispatch [::debounce-dispatch-example])}
    "debounces at 1s"]
   [:div "debounced increment:" @(rf/subscribe [::debounce-dispatch-example-val])]])

;;---
;; set toggle
;;---

(rf/reg-sub ::set-toggle-example
  (fn [db]
    (get-in db [:example ::set-toggle-example])))

(defn set-toggle-example
  []
  [:div.example
   [:h3 [:span.rf-name "::dcf/set-toggle"]]
   [:button
    {:on-click #(rf/dispatch [::dcf/set-toggle [:example ::set-toggle-example] :foo])}
    "toggle set inclusion"]
   [:div "set: " @(rf/subscribe [::set-toggle-example])]])

;;---
;; boolean toggle
;;---

(defn toggle-example
  []
  [:div.example
   [:h3 [:span.rf-name "::dcf/toggle"]]
   (let [path [:example ::toggle-boolean-example]]
     [:div
      [:button
       {:on-click #(rf/dispatch [::dcf/toggle path])}
       "boolean toggle"]
      [:div "val: " (str @(rf/subscribe [::dcf/get-in path]))]])

   (let [path [:example ::toggle-truthy-example]]
     [:div
      [:button
       {:on-click #(rf/dispatch [::dcf/toggle path :foo])}
       "truthy val toggle"]
      [:div "val: " (str @(rf/subscribe [::dcf/get-in path]))]])

   (let [path [:example ::toggle-two-val-example]]
     [:div
      [:button
       {:on-click #(rf/dispatch [::dcf/toggle path :foo :bar])}
       "two val toggle"]
      [:div "val: " (str @(rf/subscribe [::dcf/get-in path]))]])])

(defn examples
  []
  [:div
   [:h2 "donut.frontend.core.flow"]
   [debounce-dispatch-example]
   [set-toggle-example]
   [toggle-example]])
