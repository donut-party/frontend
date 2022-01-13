(ns donut.frontend.handlers
  "Donut provides handlers. Rather than registering outright,
  handlers are registered with an internal registry using the `rr`
  function in this namespace, and the `register-handlers` function is
  then used to register the handlers with re-frame. This allows users
  to specify interceptors for Donut's handlers."
  (:require [meta-merge.core :refer [meta-merge]]))

(def handlers (atom {}))

(defn register-registration
  "Store a re-frame registration in sweet little atom"
  [{:keys [id] :as registration}]
  (let [id-ns (symbol (namespace id))]
    (swap! handlers update id-ns (fn [registrations]
                                   (conj (or registrations []) registration)))))

(defn rr
  "Register a re-frame registration."
  ([reg-fn id handler-fn]
   (rr reg-fn id [] handler-fn))
  ([reg-fn id interceptors handler-fn]
   (register-registration {:reg-fn       reg-fn
                           :id           id
                           :interceptors interceptors
                           :handler-fn   handler-fn})))

(defn register-handler*
  [registration id-ns interceptors]
  (let [configured-registration (meta-merge registration
                                            {:interceptors (get interceptors id-ns)}
                                            {:interceptors (get interceptors (:id registration))})
        [reg-fn & reg-args]     (->> ((juxt :reg-fn :id :interceptors :handler-fn)
                                      (cond-> configured-registration
                                        (empty? (:interceptors configured-registration)) (dissoc :interceptors)))
                                     (filter identity))]
    (apply reg-fn reg-args)
    (into [reg-fn] reg-args)))

;; memoize to prevent superfluous re-registrations that create noisy warnings
(def register-handler (memoize register-handler*))

(defn register-handlers
  "`interceptors` is a map where the key is either a handler id or a
  namespace symbol, and the value is a vector of interceptors to add
  `into` the handlers' interceptors.

  When an `interceptors` key is a symbol, then those interceptors will
  be added to all the handlers in that ns."
  [& [interceptors]]
  (doseq [[id-ns registrations] @handlers]
    (doseq [registration registrations]
      (register-handler registration id-ns interceptors))))

(def RegisterHandlersComponent
  {:start (fn [{:keys [interceptors]}]
            (register-handlers interceptors))})
