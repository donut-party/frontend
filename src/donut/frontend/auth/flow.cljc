(ns donut.frontend.auth.flow
  (:require
   [donut.frontend.path :as p]
   [re-frame.core :as rf]))

(rf/reg-event-db ::reset-password-token-check-failed
  (fn [db]
    (assoc-in db (p/path :auth [:reset-password-token-check-failed]) true)))

(rf/reg-event-db ::clear-reset-password-token-check
  (fn [db]
    (update-in db (p/path :auth) dissoc :reset-password-token-check-failed)))

(rf/reg-sub ::reset-password-token-check-failed?
  (fn [db]
    (p/get-path db :auth [:reset-password-token-check-failed])))
