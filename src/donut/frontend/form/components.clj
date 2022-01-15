(ns donut.frontend.form.components
  (:require [donut.sugar.utils :as dsu]))

(defn- form-components-form
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [path possible-form-options]
  (if (map? possible-form-options)
    `(form-components ~path ~possible-form-options)
    `(form-components ~path)))

(defn form-subs-form
  [path possible-form-options]
  (if (map? possible-form-options)
    `(form-subs ~path ~possible-form-options)
    `(form-subs ~path)))

(defn- form-body
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defn- let-bindings
  [initial-bindings m-sym ks]
  (reduce (fn [bindings k]
            (into bindings [(symbol (str "*" (name k))) (list 'get m-sym k)]))
          initial-bindings
          ks))

(defmacro with-form
  "'Destructures' all the generated form components, prefixing them with an
  asterisk (*) to make it easierto identify them in code"
  [partial-form-path & body]
  (let [partial-form-path-name (gensym :partial-form-path)
        subs-map-name          '*form-subs
        possible-form-options  (first body)

        initial-sub-bindings
        [partial-form-path-name partial-form-path
         subs-map-name (form-subs-form partial-form-path-name
                                       possible-form-options)]

        form-components-map-name '*form-components
        initial-component-bindings
        [form-components-map-name (form-components-form partial-form-path-name
                                                        possible-form-options)]]
    `(let ~(let-bindings initial-sub-bindings
             subs-map-name
             [:form-path
              :form-state
              :form-ui-state
              :form-dscr
              :form-errors
              :form-buffer
              :form-dirty?

              :state-success?

              :sync-state
              :sync-active?
              :sync-success?
              :sync-fail?])

       (let ~(let-bindings initial-component-bindings
               form-components-map-name
               [:on-submit
                :submit-fn
                :input-opts
                :input
                :field])
         (let [~'*form (merge ~'*form-subs ~'*form-components)]
           ~@(form-body body))))))
