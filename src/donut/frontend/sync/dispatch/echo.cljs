(ns donut.frontend.sync.dispatch.echo
  (:require
   [re-frame.core :as rf]
   [re-frame.loggers :as rfl]
   [donut.frontend.handlers :as dh]
   [donut.frontend.sync.flow :as dsf]))

(dh/rr rf/reg-event-fx ::dispatch-echo
  [rf/trim-v]
  (fn [_ [opts]]
    {:fx [[:dispatch-later {:ms       (get-in opts [:echo :ms] 0)
                            :dispatch [::handle-echo opts]}]]}))

(dh/rr rf/reg-event-fx ::handle-echo
  [rf/trim-v]
  (fn [_ [opts]]
    {::handle-echo opts}))

(dh/rr rf/reg-fx ::handle-echo
  (fn [{:keys [response-handler echo]}]
    (response-handler echo)))

(defn sync-dispatch-fn
  [[_method _route-name {:keys [echo]} :as req]]
  (if-not req
    (do
      (rfl/console :error
                   "could not find route for request"
                   ::no-route-found
                   {:req req})
      (throw (js/Error. "Invalid request: could not find route for request")))
    (rf/dispatch [::dispatch-echo {:response-handler (dsf/sync-response-handler req)
                                   :echo             echo}])))
