(ns donut.frontend.reference.macros
  (:require
   [zprint.core :as zp]))


(zp/set-options! {:style :community})

(defmacro defc
  "Define a component and bind its source string to <name>-source."
  [name & body]
  (let [src (with-out-str (zp/zprint (concat (list 'defn name) body)))]
    `(do
       (defn ~name ~@body)
       (def ~(symbol (str name "-source")) ~src))))
