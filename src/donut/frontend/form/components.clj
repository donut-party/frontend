(ns donut.frontend.form.components)

(defn- form-components-form
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [path body]
  (if (map? (first body))
    `(form-components ~path ~(first body))
    `(form-components ~path)))

(defn form-subs-form
  [path body]
  (if (map? (first body))
    `(form-subs ~path ~(first body))
    `(form-subs ~path)))

(defn- form-body
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defmacro with-form
  [partial-form-path & body]
  (let [path (gensym)]
    `(let [~path ~partial-form-path
           {:keys [~'form-path
                   ~'form-state
                   ~'form-ui-state
                   ~'form-dscr
                   ~'form-errors
                   ~'form-buffer
                   ~'form-dirty?

                   ~'state-success?

                   ~'sync-state
                   ~'sync-active?
                   ~'sync-success?
                   ~'sync-fail?]
            :as ~'form-subs}
           ~(form-subs-form path body)]
       (let [{:keys [~'on-submit
                     ~'submit-fn
                     ~'input-opts
                     ~'input
                     ~'field]
              :as   ~'form-components}
             ~(form-components-form path body)
             ~'form (merge ~'form-subs ~'form-components)]
         ~@(form-body body)))))
