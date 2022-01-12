(ns donut.frontend.path
  "sweet tooth roots for the re-frame db"
  )

(def config
  {:form            [:donut :form]
   :page            [:donut :page]
   :entity          [:donut :entity]
   :nav             [:donut :nav]
   :failure         [:donut :failure]
   :reqs            [:donut :reqs]
   :system          [:donut :system]})

(defn path
  [prefix-name partial-path]
  (when-not (contains? config prefix-name)
    (throw (ex-info "invalid path prefix" prefix-name)))
  (into (prefix-name config)
        partial-path))

(defn form [partial-path]    (into [:donut :form] partial-path))
(defn page [partial-path]    (into [:donut :page] partial-path))
(defn entity [partial-path]  (into [:donut :entity] partial-path))
(defn nav [partial-path]     (into [:donut :nav] partial-path))
(defn failure [partial-path] (into [:donut :failure] partial-path))
(defn reqs [partial-path]    (into [:donut :reqs] partial-path))
(defn system [partial-path]  (into [:donut :system] partial-path))

(defn get-path
  [db prefix-name & [partial-path]]
  (get-in db (path prefix-name partial-path)))
