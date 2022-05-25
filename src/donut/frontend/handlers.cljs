(ns donut.frontend.handlers
  "Customize donut's handlers"
  (:require [re-frame.registrar :as rr]))

(defn add-interceptors
  "Allow framework consumers to add their own interceptors to donut's handlers"
  [id->interceptors]
  (doseq [[id interceptors] id->interceptors]
    (let [handler     (rr/get-handler :event id)
          new-handler (let [[default provided] (->> handler
                                                    (remove (set (map :id interceptors)))
                                                    (split-with #(= :inject-global-interceptors (:id %))))]
                        (reduce into [] [default interceptors provided]))]
      (rr/clear-handlers :event id)
      (swap! rr/kind->id->handler assoc-in [:event id] new-handler))))

(def AddInterceptorsComponent
  {:donut.system/start (fn [{:keys [:donut.system/config]}]
                         (add-interceptors (:id->interceptors config)))})
