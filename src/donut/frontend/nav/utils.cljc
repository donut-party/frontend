(ns donut.frontend.nav.utils
  (:require
   [donut.frontend.path :as p]
   [donut.sugar.utils :as dsu]
   [cemerick.url :as url]
   [clojure.string :as str]
   [clojure.walk :as walk]))

(defn query-params
  "Turn query string params into map with keyword keys"
  [path]
  (when (re-find #"\?" path)
    (-> (str/replace path #".*?\?" "")
        url/query->map
        walk/keywordize-keys)))

;; Does this belong in a pagination namespace?
(def page-param-keys
  #{:sort-by :sort-order :page :per-page})

(defn page-params
  "Extract just the page-related params from a map, converting page
  and per-page to integers"
  [params]
  (-> (select-keys params page-param-keys)
      (dsu/update-many-vals {[:page :per-page] #?(:cljs js/parseInt :clj #(Long. %))})))

(defn routed-entity
  "Returns an entity by looking up its entity-key in nav params"
  [db entity-key param-key]
  (let [[entity-key param-key] (if (and entity-key param-key)
                                 [entity-key param-key]
                                 [(p/get-path db :nav [:route :ent-type])
                                  (p/get-path db :nav [:route :id-key])])]
    (p/get-path db
                :entity
                [entity-key
                 (p/get-path db :nav [:route :params param-key])])))

(defn routed-entity-form
  [db form-key param-key]
  (p/get-path db
              :form
              (into [form-key]
                    (select-keys (p/get-path db [:nav :route :params]) [param-key]))))

(defn route
  [db]
  (p/get-path db :nav [:route]))

(defn params
  [db]
  (-> db route :params))

(defn route-name
  [db]
  (-> db route :route-name))
