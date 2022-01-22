(ns donut.frontend.reference.core.examples
  (:require
   [donut.frontend.core.flow :as dcf]
   [donut.frontend.reference.ui :as ui]
   [re-frame.core :as rf]))

;; TODO list
;; - ::dcf/assoc-in
;; - ::dcf/merge
;; - ::dcf/deep-merge
;; - expiring-component

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
  [ui/example
   [:div {:class "p-4"}
    [ui/mono-header ":donut.core.flow/debounce-dispatch"]
    [ui/explain "To debounce an action is to delay its exection for some time period,
    and to reset the delay every time the action is attempted within that time
    period."]
    [ui/explain "The button below dispatches a re-frame event after 1s, but if you click it again
    before that 1s interval is over, its delay will reset and you'll have to wait
    1s more before the re-frame event is dispatched."]
    [ui/example-offset
     [ui/button
      {:on-click #(rf/dispatch [::debounce-dispatch-example])}
      "1 second debounce"]
     [:div {:class "mt-2"} "this number should update 1s after last button click: "
      (or @(rf/subscribe [::debounce-dispatch-example-val]) 0)]]]])

;;---
;; set toggle
;;---

(rf/reg-sub ::set-toggle-example
  (fn [db]
    (get-in db [:example ::set-toggle-example])))

(defn set-toggle-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/mono-header ":donut.core.flow/set-toggle"]
    [ui/explain "This handler will toggle an element's inclusion in a set"]
    [ui/example-offset
     [ui/button
      {:on-click #(rf/dispatch [::dcf/set-toggle [:example ::set-toggle-example] :foo])}
      "toggle inclusion of :foo in set"]
     [:div {:class "mt-2"}
      "set: "
      (or @(rf/subscribe [::set-toggle-example]) #{})]]]])

;;---
;; boolean toggle
;;---

(defn toggle-example
  []
  [ui/example
   [:div {:class "p-4"}
    [ui/mono-header ":donut.core.flow/toggle"]

    [ui/explain
     "This handler's behavior differs based on the number of args you pass.
     If you pass no additional args it togles between true and nil:"]
    [ui/example-offset
     (let [path [:example ::toggle-boolean-example]]
       [:div
        [ui/button {:on-click #(rf/dispatch [::dcf/toggle path])}
         "boolean toggle"]
        [ui/example-result
         "val: " (str @(rf/subscribe [::dcf/get-in path]))]])]

    [ui/explain "If you pass in one additional arg, it toggles between that value and nil:"]
    [ui/example-offset
     (let [path [:example ::toggle-truthy-example]]
       [:div
        [ui/button {:on-click #(rf/dispatch [::dcf/toggle path :foo])}
         "truthy val toggle"]
        [ui/example-result
         "val: " (str @(rf/subscribe [::dcf/get-in path]))]])]

    [ui/explain "If you pass two additional args, it toggles between those two values:"]
    [ui/example-offset
     (let [path [:example ::toggle-two-val-example]]
       [:div
        [ui/button {:on-click #(rf/dispatch [::dcf/toggle path :foo :bar])}
         "two val toggle"]
        [ui/example-result
         "val: " (str @(rf/subscribe [::dcf/get-in path]))]])]]])

(defn examples
  []
  [:div
   [ui/h2 "donut.frontend.core.flow"]
   [debounce-dispatch-example]
   [set-toggle-example]
   [toggle-example]])
