(ns donut.frontend.core.utils
  (:require
   [ajax.url :as url]
   [donut.frontend.path :as p]
   [donut.sugar.utils :as dsu]
   [goog.object :as go]
   [reagent.core :as r]
   [reagent.ratom :as ratom])
  (:import
   [goog.async Debouncer]))

(defn prevent-default
  [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn el-by-id [id]
  (.getElementById js/document id))

(defn scroll-top
  []
  (aset (js/document.querySelector "body") "scrollTop" 0))

(defn go-get
  "Google Object Get - Navigates into a javascript object and gets a nested value"
  [obj ks]
  (let [ks (if (string? ks) [ks] ks)]
    (reduce (fn [o k]
              (try (go/get o k)
                   (catch :default _ nil)))
            obj
            ks)))

(defn go-set
  "Google Object Set - Navigates into a javascript object and sets a nested value"
  [obj ks v]
  (let [ks (if (string? ks) [ks] ks)
        target (reduce (fn [acc k]
                         (go/get acc k))
                       obj
                       (butlast ks))]
    (go/set target (last ks) v))
  obj)

(defn params-to-str
  [m]
  (->> m
       (reduce (fn [m [k v]]
                 (assoc m (dsu/full-name k) (dsu/full-name v)))
               {})
       (url/params-to-str :java)))

(defn expiring-reaction
  "Produces a reaction A' over a given reaction A that reverts
   to `expired-val` or nil after `timeout` ms"
  [sub timeout & [expired-val]]
  (let [default     expired-val
        sub-tracker (r/atom default)
        state       (r/atom default)
        debouncer   (Debouncer. #(reset! state default)
                                timeout)]
    (ratom/make-reaction #(let [sub-val  @sub
                                subt-val @sub-tracker]
                            (when (not= sub-val subt-val)
                              (reset! sub-tracker sub-val)
                              (reset! state sub-val)
                              (.fire debouncer))
                            @state))))

(defn tv
  [e]
  (go-get e ["target" "value"]))

(def loaded-scripts
  (atom #{}))

(defn load-script
  [{:keys [url on-load async]}]
  (when-not (@loaded-scripts url)
    (let [script-el (.createElement js/document "script")
          body (go-get js/window ["document" "body"])]
      (doto script-el
        (.setAttribute "src" url)
        (.setAttribute "type" "text/javascript")
        (.setAttribute "async" async))
      (.appendChild body script-el)
      (when on-load
        (.addEventListener script-el "load" on-load)))
    (swap! loaded-scripts conj url)))

(defn node-to-focus
  [ref selector]
  (if selector
    (js/document.querySelector selector)
    ref))

(defn focus-node-fn
  [& [{:keys [selector timeout]}]]
  (fn [ref]
    (when ref
      (let [focus #(.focus (node-to-focus ref selector))]
        (if timeout
          (js/setTimeout focus timeout)
          (focus))))
    ref))

;;---
;; interact with entities
;;---

(defn entities
  [db entity-type]
  (->> (get-in db (p/entity-path [entity-type]))
       vals))

(defn dissoc-entity
  [db entity-type entity-id]
  (update-in db (p/path :entity [entity-type]) dissoc entity-id))
