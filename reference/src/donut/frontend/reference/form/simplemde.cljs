(ns donut.frontend.reference.form.simplemde
  (:require
   ["react-simplemde-editor" :default SimpleMDE]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.components :as dfc]
   [clojure.string :as str]))

(defmethod dfc/input-type-opts :simplemde
  [opts]
  (-> (dfc/input-type-opts-default opts)
      (dissoc :type)))

(defn- set-change-obj-text
  [^js change-obj text]
  (->> text
       str/split-lines
       into-array
       (dcu/go-set change-obj
                   ["text"])))

(defmethod dfc/input :simplemde
  [{:keys [partial-form-path attr-path value] :as opts}]
  (let [markdown-text (atom nil)]
    [:div.simple-mde-wrapper]
    [:> SimpleMDE {:onChange  (fn [val] (dfc/dispatch-new-val partial-form-path attr-path val))
                   :onBlur    #(dfc/dispatch-input-event % opts false)
                   :value     value
                   :events    #js{:beforeChange (fn [_ change-obj]
                                                  (when-let [text @markdown-text]
                                                    (set-change-obj-text change-obj text)
                                                    (reset! markdown-text nil)))}
                   :options   {:toolbar                 ["heading"
                                                         "bold"
                                                         "italic"
                                                         "quote"
                                                         "unordered-list"
                                                         "ordered-list"]
                               :autofocus               false
                               :status                  false
                               :spellChecker            false}
                   :extraKeys {:Tab (fn [cm]
                                      (-> cm
                                          .getTextArea
                                          (.closest "form")
                                          (.querySelector
                                           "input[type=submit]")
                                          .focus))}}]))
