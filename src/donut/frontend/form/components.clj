(ns donut.frontend.form.components)

(defn- form-body
  "If the first element in the body is a map, that means it's form
  options we want to apply to every input"
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defmacro with-form
  [form-config & body]
  `(let [~'*form-config (form-config ~form-config)
         ~'*form-key    (:donut.form/key ~'*form-config)

         {:keys [~'*form-ui-state
                 ~'*form-feedback
                 ~'*form-buffer
                 ~'*form-dirty?

                 ~'*sync-state
                 ~'*sync-active?
                 ~'*sync-success?
                 ~'*sync-fail?]
          :as ~'*form-subs}
         (form-subs ~'*form-config)

         {:keys [~'*sync-form
                 ~'*input-opts
                 ~'*input
                 ~'*input-builder
                 ~'*field
                 ~'*attr-buffer]
          :as   ~'*form-components}
         (form-components ~'*form-config)

         ~'*form (merge ~'*form-subs ~'*form-components)]
     ~@(form-body body)))
