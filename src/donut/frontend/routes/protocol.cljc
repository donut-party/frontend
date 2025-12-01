(ns donut.frontend.routes.protocol)

(defprotocol Router
  (path
    [this req]
    "generate a path")

  (req-id
    [this req]
    "a req-id is used to distinguish multiple requests to the same
    resource by their params for sync bookkeeping")

  ;; TODO update this to just take request
  (route
    [this path-or-name]
    [this path-or-name route-params]
    [this path-or-name route-params query-params]
    "Given a path OR a route name, return the route that corresponds"))

(defmulti router :use)
