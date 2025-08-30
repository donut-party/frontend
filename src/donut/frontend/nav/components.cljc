(ns donut.frontend.nav.components
  (:require
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.routes :as dfr]
   [re-frame.core :as rf]))



(defn simple-route-link
  [{:keys [route-name route-params query-params] :as link-opts}
   & children]
  (into [:a (merge {:href (dfr/path route-name route-params query-params)}
                   (dissoc link-opts :route-name :route-params :query-params))]
        children))

(defn route-link
  [{:keys [route-name route-params query-params class current-class]
    :as link-opts}
   & children]
  (let [current-route (rf/subscribe [::dnf/route-name])
        link-opts (assoc link-opts :class (if (and current-class (= @current-route route-name))
                                            current-class
                                            class))]
    (into [:a (merge {:href (dfr/path route-name route-params query-params)}
                     (dissoc link-opts :route-name :route-params :query-params :active-class))]
          children)))
