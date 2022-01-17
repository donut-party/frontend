(ns donut.frontend.form.components)

(defn- form-body
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defmacro with-form
  [partial-form-path & body]
  (let [path                   (gensym :partial-form-path)
        possible-formwide-opts (first body)
        possible-formwide-opts (when (map? possible-formwide-opts)
                                 possible-formwide-opts)]
    `(let [~path            ~partial-form-path
           ~'*formwide-opts (update ~possible-formwide-opts
                                    :*sync-key
                                    #(or % (donut.frontend.sync.flow/sync-key ~path)))
           ~'*sync-key      (:*sync-key ~'*formwide-opts)

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
           (form-subs ~path ~'*formwide-opts)]
       (let [{:keys [~'*submit-fn
                     ~'*input-opts
                     ~'*input
                     ~'*field]
              :as   ~'*form-components}
             (form-components ~path ~'*formwide-opts)
             ~'*form (merge ~'*form-subs ~'*form-components)]
         ~@(form-body body)))))
