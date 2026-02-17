(ns donut.frontend.form.components.inline-input
  (:require
   [donut.compose :as dc]
   [donut.frontend.core.flow :as dfcf]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.components :as dfc]
   [donut.frontend.form.flow :as dff]
   [re-frame.core :as rf]
   [reagent.core :as r]))

(def Opts
  [:map
   [:form-data]
   [:form-config]
   [:value-component]
   [:syncing-component]])

;; TODO add submit-key-bindings and cancel-key-bindings options
(defn inline-input
  "component that toggles between displaying value and input"
  [{:keys [*form
           form-data
           show-form?]}]
  (let [ref    (atom nil)
        ref-fn (fn [this]
                 (reset! ref this)
                 ((dcu/focus-node-fn) this))
        {:keys [::dff/form-key *input]} *form]
    (r/create-class
     ;; an alternative to doing this on mount would be to create a function that
     ;; ties changing show-form? with dispatching set-form
     {:component-did-mount
      (fn []
        (dcu/add-key-listener
         {:enter  {:any-modifier (fn [_ ref] (.blur ref))}
          :escape {:any-modifier (fn [_ _] (reset! show-form? false))}
          :ref    @ref})
        (rf/dispatch
         [::dff/set-form {::dff/form-key  form-key
                          ::dff/form-data form-data}]))

      :reagent-render
      (fn [{:keys [input-opts]}]
        (rf/dispatch
         [::dff/set-form {::dff/form-key  form-key
                          ::dff/form-data form-data}])
        [*input
         (merge
          {:ref ref-fn
           :on-blur                      (dc/wrap (fn [event-handler]
                                                    (fn wrap-stop-showing-form [e]
                                                      (event-handler e)
                                                      (rf/dispatch [::dfcf/apply-fn (fn [] (reset! show-form? false))]))))
           :donut.input/interaction-mode :donut.input/inline}
          input-opts)])})))

(defn togglable-inline-input
  [{:keys [form-config] :as _opts}]
  (dfc/with-form form-config
    (let [show-form?         (r/atom false)]
      (fn [{:keys [value-component syncing-component] :as opts}]
        (let [sub-component-opts (assoc opts
                                        :*form *form
                                        :show-form? show-form?)
              syncing-component (or syncing-component value-component)]
          (cond
            @*sync-active? [syncing-component sub-component-opts]
            @show-form?    [inline-input sub-component-opts]
            :else          [value-component sub-component-opts]))))))
