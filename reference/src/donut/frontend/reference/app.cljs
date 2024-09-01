(ns donut.frontend.reference.app
  (:require
   [donut.frontend.nav.components :as dnc]
   [donut.frontend.nav.flow :as dnf]
   donut.frontend.reference.frontend-routes ;; for reloading
   [re-frame.core :as rf]))

(def sidenav-classes
  "text-gray-600 hover:bg-gray-50 hover:text-gray-900
   group flex items-center px-2 py-2 text-sm font-medium rounded-md")

(def sidenav-active-classes
  "bg-gray-100 text-gray-900 group flex items-center
   px-2 py-2 text-sm font-medium rounded-md")

(defn sidenav-link
  [route-name text]
  [dnc/route-link {:route-name   route-name
                   :class        sidenav-classes
                   :active-class sidenav-active-classes}
   text])

(defn app
  []
  [:div {:class ""}
   [:div {:class "hidden md:flex md:w-64 md:flex-col md:fixed md:inset-y-0"}
    [:div {:class "flex-1 flex flex-col min-h-0 border-r border-gray-200"}
     [:div {:class "flex-1 flex flex-col pb-4 overflow-y-auto"}
      [:nav {:class "pt-4 flex-1 px-2 bg-white space-y-1"}
       [sidenav-link :home "home"]
       [sidenav-link :core.flow "core"]
       [sidenav-link :nav.flow "nav"]
       [sidenav-link :sync.flow "sync"]
       [sidenav-link :form.flow "form"]]]]]
   [:div {:class "md:pl-64 flex flex-col flex-1"}
    [:main {:class "flex-1"}
     [:div {:class "max-w-7xl mx-auto py-6 px-4 sm:px-6 md:px-8"}
      @(rf/subscribe [::dnf/routed-component :main])]]]])
