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
  [:div {:class "p-4"}
   [ui/mono-header ":donut.core.flow/debounce-dispatch"]
   [:div {:class "my-3"}
    [ui/explain "To debounce an action is to delay its exection for some time period,
    and to reset the delay every time the action is attempted within that time
    period."]
    [ui/explain "The button below dispatches a re-frame event after 1s, but if you click it again
    before that 1s interval is over, its delay will reset and you'll have to wait
    1s more before the re-frame event is dispatched."]]
   [ui/example-offset
    [ui/button
     {:on-click #(rf/dispatch [::debounce-dispatch-example])}
     "1 second debounce"]
    [:div {:class "mt-2"} "this number should update 1s after last button click: "
     (or @(rf/subscribe [::debounce-dispatch-example-val]) 0)]]])

;;---
;; set toggle
;;---

(rf/reg-sub ::set-toggle-example
  (fn [db]
    (get-in db [:example ::set-toggle-example])))

(defn set-toggle-example
  []
  [:div {:class "p-4"}
   [ui/mono-header ":donut.core.flow/set-toggle"]
   [:div {:class "my-3"}
    [ui/explain "This handler will toggle an element's inclusion in a set"]]
   [ui/example-offset
    [ui/button
     {:on-click #(rf/dispatch [::dcf/set-toggle [:example ::set-toggle-example] :foo])}
     "toggle inclusion of :foo in set"]
    [:div {:class "mt-2"}
     "set: "
     (or @(rf/subscribe [::set-toggle-example]) #{})]]])

;;---
;; boolean toggle
;;---

(defn toggle-example
  []
  [:div {:class "py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6"}
   [:dt {:class ""} [ui/mono-header ":donut.core.flow/toggle"]]
   [:dd {:class "mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2"}
    (let [path [:example ::toggle-boolean-example]]
      [:div
       [ui/button
        {:on-click #(rf/dispatch [::dcf/toggle path])}
        "boolean toggle"]
       [:div "val: " (str @(rf/subscribe [::dcf/get-in path]))]])

    (let [path [:example ::toggle-truthy-example]]
      [:div
       [ui/button
        {:on-click #(rf/dispatch [::dcf/toggle path :foo])}
        "truthy val toggle"]
       [:div "val: " (str @(rf/subscribe [::dcf/get-in path]))]])

    (let [path [:example ::toggle-two-val-example]]
      [:div
       [ui/button
        {:on-click #(rf/dispatch [::dcf/toggle path :foo :bar])}
        "two val toggle"]
       [:div "val: " (str @(rf/subscribe [::dcf/get-in path]))]])]])

(defn examples
  []
  [:div
   [ui/h2 "donut.frontend.core.flow"]
   [ui/example
    [:dl {:class "sm:divide-y sm:divide-gray-200"}
     [debounce-dispatch-example]
     [set-toggle-example]
     [toggle-example]]]])
