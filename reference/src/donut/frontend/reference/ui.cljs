(ns donut.frontend.reference.ui
  (:require [donut.frontend.nav.components :as dnc]))

(defn h1
  [& children]
  (into [:h1 {:class "text-2xl font-semibold text-gray-900"}]
        children))

(defn h2
  [& children]
  (into [:h2 {:class "text-xl font-semibold text-gray-900"}]
        children))

(defn h3
  [& children]
  (into [:h3 {:class "text-lg font-semibold text-gray-900"}]
        children))

(def a-class
  "text-blue-800 hover:text-green-500")

(defn a
  [opts & components]
  (into [:a (merge {:class a-class} opts)]
        components))

(defn route-link
  [opts & children]
  (into [dnc/route-link (merge {:class a-class} opts)]
        children))
