(ns donut.frontend.reference.ui)

(defn h1
  [& children]
  (into [:h1 {:class "text-2xl font-semibold text-gray-900"}]
        children))
