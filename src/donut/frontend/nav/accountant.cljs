(ns donut.frontend.nav.accountant
  "Adapted from Accountant, https://github.com/venantius/accountant
  Accountant is licensed under the EPL v1.0."
  (:require [goog.events :as events]
            [goog.events.EventType]
            [goog.history.EventType :as EventType])
  (:import goog.history.Event
           goog.history.Html5History
           [goog Uri]))


(def app-updated-token? (atom false))

(defn- transformer-create-url
  [token path-prefix _location]
  (str path-prefix token))

(defn- transformer-retrieve-token
  [_path-prefix location]
  (str (.-pathname location) (.-search location) (.-hash location)))

(defn new-history
  []
  (let [transformer (goog.history.Html5History.TokenTransformer.)]
    (set! (.. transformer -retrieveToken) transformer-retrieve-token)
    (set! (.. transformer -createUrl) transformer-create-url)
    (doto (Html5History. js/window transformer)
      (.setUseFragment false)
      (.setPathPrefix "")
      (.setEnabled true))))

(defn dispatch-on-navigate
  [history nav-handler]
  (events/listen
   history
   EventType/NAVIGATE
   (fn [e]
     (if-not @app-updated-token?
       (let [token (.-token e)]
         (nav-handler token))
       (reset! app-updated-token? false)))))

(defn- get-href-attribute
  "Given a DOM node, if it is an element node, return its href attribute.
  Otherwise, return nil."
  [node]
  (when (and node (= (.-nodeType node) js/Node.ELEMENT_NODE))
    (.getAttribute node "href")))

(defn- find-href-node
  "Given a DOM element that may or may not be a link, traverse up the DOM tree
  to see if any of its parents are links. If so, return the href content, if
  it does not have an explicit `data-trigger` attribute to signify a non-navigational
  link element."
  [e]
  (let [href (get-href-attribute e)
        attrs (.-attributes e)
        navigation-link? (and href attrs (-> attrs (aget "data-trigger") not))]
    (if navigation-link?
      e
      (when-let [parent (.-parentNode e)]
        (recur parent)))))

(defn- uri->query [^Uri uri]
  (let [query (.getQuery uri)]
    (when-not (empty? query)
      (str "?" query))))

(defn- uri->fragment [^Uri uri]
  (let [fragment (.getFragment uri)]
    (when-not (empty? fragment)
      (str "#" fragment))))

(defn prevent-reload-on-known-path
  "Create a click handler that blocks page reloads for known routes"
  [history path-exists? reload-same-path? update-token]
  (events/listen
   js/document
   "click"
   (fn [e]
     (let [target (.-target e)
           button (.-button e)
           meta-key (.-metaKey e)
           alt-key (.-altKey e)
           ctrl-key (.-ctrlKey e)
           shift-key (.-shiftKey e)
           any-key (or meta-key alt-key ctrl-key shift-key)
           href-node (find-href-node target)
           href (when href-node (.-href href-node))
           link-target (when href-node (.-target href-node))
           uri ^Uri (.parse Uri href)
           path (.getPath uri)
           query (uri->query uri)
           fragment (uri->fragment uri)
           relative-href (str path query fragment)
           title (.-title target)
           host (.getDomain uri)
           port (.getPort uri)
           current-host js/window.location.hostname
           current-port js/window.location.port
           loc js/window.location
           current-relative-href (str (.-pathname loc)
                                      (or (.-query loc) (.-search loc))
                                      (.-hash loc))]
       (when (and (not any-key)
                  (#{"" "_self"} link-target)
                  (= button 0)
                  (= host current-host)
                  (or (not port)
                      (= (str port) (str current-port)))
                  (path-exists? path))
         (when (not= current-relative-href relative-href) ;; do not add duplicate html5 history state
           (update-token relative-href title))
         (.preventDefault e)
         (.stopPropagation e)
         (when reload-same-path?
           (events/dispatchEvent history (Event. path true))))))))
