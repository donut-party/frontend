(ns donut.frontend.core.utils
  (:require
   [ajax.url :as url]
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
    (reduce go/get obj ks)))

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
       (map (fn [[k v]]
              [(if (keyword? k) (subs (str k) 1) k)
               (if (keyword? v) (subs (str v) 1) v)]))
       (into {})
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
