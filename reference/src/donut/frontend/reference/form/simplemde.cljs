(ns donut.frontend.reference.form.simplemde
  (:require
   ["react-simplemde-editor" :default SimpleMDE]
   [clojure.string :as str]
   [donut.frontend.core.utils :as dcu]
   [donut.frontend.form.components.input :as dfci]))

(defmethod dfci/base-donut-input-opts-for-type :simplemde
  [opts]
  (-> (dfci/default-base-donut-input-opts opts)
      (dissoc :type)))

(defn- set-change-obj-text
  [^js change-obj text]
  (->> text
       str/split-lines
       into-array
       (dcu/go-set change-obj
                   ["text"])))

(defmethod dfci/input :simplemde
  [{:donut.input/keys [value] :as opts}]
  (let [markdown-text (atom nil)]
    [:> SimpleMDE {:onChange  (fn [val] (dfci/dispatch-new-value opts val))
                   :onBlur    (fn [e] (dfci/dispatch-attr-input-event e opts))
                   :value     value
                   :events    #js{:beforeChange (fn [_ change-obj]
                                                  (when-let [text @markdown-text]
                                                    (set-change-obj-text change-obj text)
                                                    (reset! markdown-text nil)))}
                   :options   {:toolbar      ["heading"
                                              "bold"
                                              "italic"
                                              "quote"
                                              "unordered-list"
                                              "ordered-list"]
                               :autofocus    false
                               :status       false
                               :spellChecker false}
                   :extraKeys {:Tab (fn [cm]
                                      (-> cm
                                          .getTextArea
                                          (.closest "form")
                                          (.querySelector "input[type=submit]")
                                          .focus))}}]))
