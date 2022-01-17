(ns donut.frontend.form.components)

(defn- form-components-form
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [path possible-formwide-opts]
  (if (map? possible-formwide-opts)
    `(form-components ~path ~possible-formwide-opts)
    `(form-components ~path)))

(defn form-subs-form
  [path possible-formwide-opts]
  (if (map? possible-formwide-opts)
    `(form-subs ~path ~possible-formwide-opts)
    `(form-subs ~path)))

(defn- form-body
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defn sync-key-binding
  [path possible-formwide-opts]
  (if (map? possible-formwide-opts)
    `[~'*formwide-opts ~possible-formwide-opts
      ~'*sync-key (:*sync-key ~'*formwide-opts)]
    `[~'*sync-key ~path]))

(defmacro with-form
  [partial-form-path & body]
  (let [path                   (gensym :partial-form-path)
        possible-formwide-opts (first body)
        possible-formwide-opts (if (map? possible-formwide-opts)
                                 (update possible-formwide-opts :*sync-key #(or % path))
                                 possible-formwide-opts)]
    `(let [~path ~partial-form-path
           ~@(sync-key-binding path possible-formwide-opts)
           {:keys [~'*form-path
                   ~'*form-ui-state
                   ~'*form-feedback
                   ~'*form-errors
                   ~'*form-buffer
                   ~'*form-dirty?

                   ~'*state-success?

                   ~'*sync-state
                   ~'*sync-active?
                   ~'*sync-success?
                   ~'*sync-fail?]
            :as ~'*form-subs}
           ~(form-subs-form path possible-formwide-opts)]
       (let [{:keys [~'*submit-fn
                     ~'*input-opts
                     ~'*input
                     ~'*field]
              :as   ~'*form-components}
             ~(form-components-form path possible-formwide-opts)
             ~'*form (merge ~'*form-subs ~'*form-components)]
         ~@(form-body body)))))
