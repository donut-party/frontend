(ns donut.frontend.config
  (:require
   [donut.frontend.handlers :as dh]))

(def default-config
  {:donut.system/defs
   {:donut.frontend
    {:handlers dh/RegisterHandlersComponent}}})
