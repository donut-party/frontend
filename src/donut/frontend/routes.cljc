(ns donut.frontend.routes
  (:require
   #?@(:clj [[clojure.java.io :as io]
             [clojure.pprint :as pp]
             [clojure.walk :as walk]])
   [donut.frontend.routes.protocol :as drp]))

(def frontend-router nil)
(def sync-router nil)

(defn path
  [route-name & [route-params query-params]]
  (drp/path frontend-router route-name route-params query-params))

(defn route
  [path-or-name & [route-params query-params]]
  (drp/route frontend-router path-or-name route-params query-params))

(defn api-path
  [route-name & [route-params query-params]]
  (drp/path sync-router route-name route-params query-params))

(defn req-id
  [route-name & [route-params]]
  (drp/req-id sync-router route-name route-params))

(defn start-frontend-router
  [{:keys [:donut.system/config]}]
  (set! frontend-router (drp/router config)))

(defn start-sync-router
  [{:keys [:donut.system/config]}]
  (set! sync-router (drp/router config)))

#?(:clj
   (do
     (def route-registry (atom []))
     (defmacro register-routes
       [routes]
       (reset! route-registry routes)
       routes)
     (defn write-routes
       []
       (let [filename "src/donut/generated/frontend_routes.cljc"]
         (io/make-parents filename)
         (when (or (not-empty @route-registry)
                   (not (.exists (io/file filename))))
           (let [allowed-keys #{:name :ent-type :id-key}]
             (spit "src/donut/generated/frontend_routes.cljc"
                   (str "(ns donut.generated.frontend-routes
  \"do not modify this! this is auto-generated by donut. it contains frontend routes for the backend to read.\")

(def routes
"
                        (with-out-str
                          (pp/pprint
                           (walk/postwalk (fn [x]
                                            (if (map? x)
                                              (select-keys x allowed-keys)
                                              x))
                                          @route-registry)))
                        ")"))))))))

#?(:clj
   (do
     (def ^:private cross-compiled-allowed-route-keys
       #{:name :ent-type :id-key})
     (defn cross-compiled-routes
       [route-registry routes]
       (walk/postwalk (fn [x]
                        (cond
                          (map? x)
                          (select-keys x cross-compiled-allowed-route-keys)

                          (symbol? x)
                          (let [fully-qualified-name (if (namespace x)
                                                       x
                                                       (symbol (str *ns*) (name x)))]
                            (get route-registry fully-qualified-name nil))

                          :else
                          x))
                      routes))

     (def defroute-registry (atom {}))

     (defmacro defroutes
       [var-name routes]
       (let [route-sym (symbol (str *ns*) (str var-name))]
         (swap! defroute-registry assoc route-sym (cross-compiled-routes @defroute-registry routes)))
       (list 'def var-name routes))

     (defn write-defroutes
       ([route-var-name]
        (write-defroutes route-var-name "src/donut/generated/frontend_routes.cljc"))
       ([route-var-name filename ]
        (io/make-parents filename)
        (spit "src/donut/generated/frontend_routes.cljc"
              (str "(ns donut.generated.frontend-routes
  \"do not modify this! this is auto-generated by donut. it contains frontend routes for the backend to read.\")

(def routes
"
                   (let [defd-routes (get @defroute-registry route-var-name)]
                     (with-out-str
                       (pp/pprint
                        (if (list? defd-routes)
                          (eval defd-routes)
                          defd-routes)))) ")"))))

     (defn write-frontend-routes-hook
       {:shadow.build/stage :flush}
       [build-state route-var-name & _]
       (write-defroutes route-var-name)
       build-state)))
