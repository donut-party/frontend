(ns donut.frontend.filter.flow
  "Easy-ish tools for creating subscriptions that filter other
  subscriptions, typically used with form inputs"
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [donut.frontend.form.flow :as dcf]))

(defn filter-contains-text
  "Filters `xs` returning those where any val (or vals corresponding to
  `x-keys`) contain `query`, case-insensitive.

  `val-transform` is applied to each val, defaulting to `str`. It can
  sometimes be useful, for example, to filter numbers as text rather
  than numbers. It can also be used to 'filter' vals by returning
  `nil`"
  [_
   query
   [{:keys [queried-keys mapping min-length]
     :or   {mapping    str
            min-length 0}}]
   xs]
  (if (<= (count query) min-length)
    xs
    (let [query (str/lower-case query)]
      (filter (fn [x]
                (not= -1
                      (.indexOf (->> (if (seq queried-keys)
                                       (select-keys x queried-keys)
                                       x)
                                     vals
                                     (map mapping)
                                     (str/join " ")
                                     (str/lower-case))
                                query)))
              xs))))

(defn filter-toggle
  "Restrict xs to vals whose `form-attr` is truthy, if `toggle?` is true"
  [form-attr apply-toggle? _ xs]
  (if apply-toggle?
    (filter form-attr xs)
    xs))

(defn filter-attr-compare
  "Compares x val to `attr-val` when `attr-val` is not `nil`"
  [form-attr attr-val [comp-fn key-fn] xs]
  (if (some? attr-val)
    (let [key-fn (or key-fn form-attr)]
      (filter #(let [x-val (key-fn %)]
                 (comp-fn x-val attr-val)) xs))
    xs))

(defn filter-attr=
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [= key-fn] xs))

(defn filter-attr>
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [> key-fn] xs))

(defn filter-attr<
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [< key-fn] xs))

(defn filter-attr>=
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [>= key-fn] xs))

(defn filter-attr<=
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [<= key-fn] xs))

(defn filter-set
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [#(contains? %2 %1) key-fn] xs))

(defn apply-filter-fns
  [unfiltered form-data filter-fns]
  (reduce (fn [unfiltered [form-attr filter-fn & filter-args]]
            (filter-fn form-attr (form-attr form-data) filter-args unfiltered))
          unfiltered
          filter-fns))

(defn reg-filtered-sub
  "Usage:
  (reg-filtered-sub
    :sub-name
    :source-sub-name
    [:form-name :method]
    [[:attr-1 filter-fn]
     [:attr-2 filter-fn]])

  For example:

  (reg-filtered-sub
    :filtered-todo-lists
    :all-todos
    [:todo-filter-form]
    [[:name filter-attr=]])

  creates a subscription named `:filtered-todo-lists`. It has an input
  subscription named `:all-todos`. `:all-todos` is filtered using the
  `:buffer` value of a form at `:todo-filter-form`.

    The value of the `:name` attribute of `:todo-filter-form` is used to
  filter todos in `:all-todos` such that the result contains todos
  that have a `:name` that's equal to the value in `:todo-filter-form`.

  Another example:

  (reg-filtered-sub
    :filtered-todo-lists
    :all-todos
    [:todo-filter-form]
    [[:query filter-attr=]])

  This is similar to the previous example. The difference is that
  you're checking whether `:query` text is found anywhere in a todo.
  `:all-todos` is filtered such that a todo is returned if any of its
  `vals` contains the text in the `:query` attribute of
  `:todo-filter-form`."
  [sub-name {:keys [source-sub-name filter-form-path filter-fns]}]
  (rf/reg-sub sub-name
    :<- [source-sub-name]
    :<- [::dcf/buffer filter-form-path]
    (fn [[unfiltered form-data] _]
      (apply-filter-fns unfiltered form-data filter-fns))))
