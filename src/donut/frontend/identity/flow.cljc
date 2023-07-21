(ns donut.frontend.identity.flow
  (:require
   [donut.frontend.path :as p]
   [re-frame.core :as rf]))

(rf/reg-event-db ::reset-password-token-check-failed
  (fn [db]
    (assoc-in db (p/path :identity [:reset-password-token-check-failed]) true)))

(rf/reg-event-db ::clear-reset-password-token-check
  (fn [db]
    (update-in db (p/path :identity) dissoc :reset-password-token-check-failed)))

(rf/reg-sub ::reset-password-token-check-failed?
  (fn [db]
    (p/get-path db :identity [:reset-password-token-check-failed])))
