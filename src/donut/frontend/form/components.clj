(ns donut.frontend.form.components
  (:require
   [donut.frontend.sync.flow :as dsf]))

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
                                 (update possible-formwide-opts :*sync-key #(or % path)))]
    `(let [~path            ~partial-form-path
           ~'*formwide-opts ~possible-formwide-opts
           ;; every form has its sync-key set explicitly, either passed or using
           ;; a default
           ~'*sync-key      (or (:*sync-key ~'*formwide-opts)
                                (dsf/sync-key ~path))
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
