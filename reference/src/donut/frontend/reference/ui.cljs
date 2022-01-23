(ns donut.frontend.reference.ui
  (:require [donut.frontend.nav.components :as dnc]))

(defn h1
  [& children]
  (into [:h1 {:class "text-3xl font-extrabold text-gray-900"}]
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

(defn example
  [& children]
  [:div {:class "my-5 max-w-4xl"}
   (into [:div {:class "bg-white shadow overflow-hidden sm:rounded-lg"}]
         children)])

(defn example-header
  [& children]
  [:div {:class "px-4 py-5 sm:px-6"}
   [h3 "::dcf/set-toggle"]])

(defn mono-header
  [& children]
  (into [:h2 {:class "font-mono text-sky-600 text-med font-semibold"}]
        children))

(def button-class
  "inline-flex items-center px-2 py-1 border border-transparent
   text-sm font-medium rounded-md shadow-sm
   bg-gray-200 hover:bg-gray-300 focus:outline-none
   focus:ring-2 focus:ring-offset-2 focus:ring-gray-500")

(defn button
  [opts & children]
  (into [:button (merge {:class button-class}
                        opts)]
        children))

(defn explain
  [text]
  [:p {:class "text-gray-600 my-3"}
   text])

(defn example-offset
  [& children]
  (into [:div {:class "p-4 bg-zinc-50 rounded-md border border-gray-300"}]
        children))

(defn example-result
  [& children]
  (into [:div {:class "mt-2"}]
        children))

(defn pprint
  [data]
  [:pre {:class "text-sm font-mono"}
   (with-out-str (cljs.pprint/pprint data))])
