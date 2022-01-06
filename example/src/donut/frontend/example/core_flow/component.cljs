(ns donut.frontend.example.core-flow.component
  (:require [re-frame.core :as rf]
            [donut.frontend.core.flow :as dcf]))

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
   [:div
    [:h3 "debounce"]
    [:button
     {:on-click #(rf/dispatch [::debounce-dispatch-example])}
     "debounces at 1s"]
    [:div "debounced increment:" @(rf/subscribe [::debounce-dispatch-example-val])]]])

(defn examples
  []
  [:div
   [:h2 "donut.frontend.core.flow"]
   [debounce-dispatch-example]])
