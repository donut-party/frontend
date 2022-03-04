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
  (let [possible-form-config (first body)
        possible-form-config (when (map? possible-form-config)
                               possible-form-config)]
    `(let [~'*form-key    ~form-key
           ~'*form-config (-> {:donut.form/key   ~'*form-key
                               :donut.form/sync? true
                               :donut.sync/key   (donut.frontend.sync.flow/sync-key ~'*form-key)}
                              (merge ~possible-form-config))
           ~'*sync-key    (:*sync-key ~'*form-config)
           ~'*form-layout (select-keys ~'*form-config form-layout-keys)

           {:keys [~'*form-ui-state
                   ~'*form-feedback
                   ~'*form-buffer
                   ~'*form-dirty?

                   ~'*sync-state
                   ~'*sync-active?
                   ~'*sync-success?
                   ~'*sync-fail?]
            :as ~'*form-subs}
           (form-subs ~'*form-config)]
       (let [{:keys [~'*submit
                     ~'*input-opts
                     ~'*input
                     ~'*field]
              :as   ~'*form-components}
             (form-components ~'*form-config)
             ~'*form (merge ~'*form-subs ~'*form-components)]
         ~@(form-body body)))))
