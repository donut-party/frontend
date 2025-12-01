(ns donut.frontend.pagination.components
  (:require
   [re-frame.core :as rf]
   [donut.frontend.pagination.flow :as dpf]
   [donut.frontend.nav.flow :as dnf]
   [donut.frontend.routes :as dfr]))

(defn window
  "The central set of pages to include with links"
  [page-count current-page window-size]
  (let [n      (Math/floor (/ (dec window-size) 2))
        l      (- current-page n)
        r      (+ current-page n)
        l-diff (when (< l 1) (- 1 l))
        r-diff (when (> r page-count) (- r page-count))

        [l r] (cond->> [(- current-page n) (+ current-page n)]
                l-diff (map #(+ % l-diff))
                r-diff (map #(- % r-diff)))]
    (vec (range l (inc r)))))

(defn page-subset
  "Which pages, out of all, to include in in page-nav"
  [page-count current-page window-size]
  (let [central-range (window page-count current-page window-size)]
    (cond->> central-range
      (> (first central-range) 2)              (into [nil])
      (not= (first central-range) 1)           (into [1])
      (< (last central-range)(dec page-count)) (#(into % [nil]))
      (not= (last central-range) page-count)   (#(into % [page-count])))))

;; TODO revisit this
(defn page-nav
  "A component that displays a link to each page. Current page has the
  `active-class` class (`active` by default)"
  [{:keys [pager-id window-size active-class
           space-component link-component]}]
  (let [{:keys [query page-count]}                    @(rf/subscribe [::dpf/pager pager-id])
        ;; TODO should path-params be route-params? believe it's the return
        ;; value of reitit match
        {:keys [path-params query-params route-name]} @(rf/subscribe [::dnf/route])
        current-page                                  (:page query)
        page-nums                                     (if (and window-size (< window-size page-count))
                                                        (page-subset page-count current-page window-size)
                                                        (range 1 (inc page-count)))]

    (->> page-nums
         (map (fn [page]
                (if page
                  (let [href         (dfr/path {:route-name   route-name
                                                :route-params path-params
                                                :query-params (assoc query-params :page page)})
                        active-page? (= (:page query) page)]
                    (if link-component
                      [link-component {:href         href
                                       :active-page? active-page?
                                       :active-class active-class
                                       :page         page}]
                      [:a.page-num
                       {:href  href
                        :class (when active-page? (or active-class "active"))}
                       page]))
                  (or space-component
                      [:span.page-space [:i.fal.fa-ellipsis-h]]))))
         (into [:div.pager]))))
