(ns donut.frontend.form.components)

(defn- form-body
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defmacro with-form
  [form-key & body]
  (let [possible-formwide-opts (first body)
        possible-formwide-opts (when (map? possible-formwide-opts)
                                 possible-formwide-opts)]
    `(let [~'*form-key      ~form-key
           ~'*formwide-opts (-> {:donut.form/key   ~'*form-key
                                 :donut.form/sync? true
                                 :donut.sync/key   (donut.frontend.sync.flow/sync-key ~'*form-key)}
                                (merge ~possible-formwide-opts))
           ~'*sync-key      (:*sync-key ~'*formwide-opts)

           {:keys [~'*form-ui-state
                   ~'*form-feedback
                   ~'*form-errors
                   ~'*form-buffer
                   ~'*form-dirty?

                   ~'*sync-state
                   ~'*sync-active?
                   ~'*sync-success?
                   ~'*sync-fail?]
            :as ~'*form-subs}
           (form-subs ~'*formwide-opts)]
       (let [{:keys [~'*submit
                     ~'*input-opts
                     ~'*input
                     ~'*field]
              :as   ~'*form-components}
             (form-components ~'*formwide-opts)
             ~'*form (merge ~'*form-subs ~'*form-components)]
         ~@(form-body body)))))
