(ns donut.frontend.path
  "donut roots for the re-frame db. helpers for those roots.")

(def config
  {:auth             [:donut :auth]
   :donut-component  [:donut :system :donut.system/instances :donut.frontend]
   :entity           [:donut :entity]
   :failure          [:donut :failure]
   :form             [:donut :form]
   :nav              [:donut :nav]
   :nav-buffer       [:donut :nav :buffer]
   :page             [:donut :page]
   :reqs             [:donut :reqs]
   :system           [:donut :system]
   :system-component [:donut :system :donut.system/instances]})

(defn path
  [prefix-name & [partial-path]]
  (when-not (contains? config prefix-name)
    (throw (ex-info "invalid path prefix" {:prefix prefix-name})))
  (cond-> (prefix-name config)
    (some? partial-path) (into partial-path)))

(defn entity-path [partial-path]  (into [:donut :entity] partial-path))
(defn failure-path [partial-path] (into [:donut :failure] partial-path))
(defn form-path [partial-path]    (into [:donut :form] partial-path))
(defn auth-path [partial-path]    (into [:donut :auth] partial-path))
(defn nav-path [partial-path]     (into [:donut :nav] partial-path))
(defn page-path [partial-path]    (into [:donut :page] partial-path))
(defn reqs-path [partial-path]    (into [:donut :reqs] partial-path))
(defn system-path [partial-path]  (into [:donut :system] partial-path))

(defn get-path
  [db prefix-name & [partial-path]]
  (get-in db (path prefix-name partial-path)))
