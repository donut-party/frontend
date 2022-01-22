(ns donut.frontend.nav.components
  (:require
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.routes :as dfr]
   [re-frame.core :as rf]))



(defn simple-route-link
  [{:keys [route-name route-params query-params] :as link-opts}
   child]
  [:a (merge {:href (dfr/path route-name route-params query-params)}
             (dissoc link-opts :route-name :route-params :query-params))
   child])

(defn route-active?
  "returns true if given route name is the same as the current route name, or if
  it's a 'parent'"
  [route-name]
  (re-find (re-pattern (str "^" route-name))
           (str @(rf/subscribe [::dnf/route-name]))))

(defn route-link
  [{:keys [route-name route-params query-params
           class active-class]
    :as link-opts}
   child]
  (let [link-opts (assoc link-opts :class (if (and (route-active? route-name) active-class)
                                            active-class
                                            class))]
    [:a (merge {:href (dfr/path route-name route-params query-params)}
               (dissoc link-opts :route-name :route-params :query-params :active-class))
     child]))
