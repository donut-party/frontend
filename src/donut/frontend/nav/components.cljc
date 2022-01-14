(ns donut.frontend.nav.components
  (:require
   [donut.frontend.routes :as dfr]))

(defn route-link
  ([route-name child]
   (route-link route-name nil nil child))
  ([route-name route-params child]
   (route-link route-name route-params nil child))
  ([route-name route-params query-params child]
   [:a {:href  (dfr/path route-name route-params query-params)}
    child]))

(defn route-link-opts
  "Experimental approach to modifying components"
  [route-link-component opts]
  (update-in route-link-component [0 :href] merge opts))
