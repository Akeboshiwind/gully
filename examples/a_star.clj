(ns a-star
  "Implement the A* search algorithm."
  (:require [gully.core :as g]
            [gully.strategy :as gs]))

(comment
  ;; This is how you would use the SearchStrategy to do a simple search (outside of gully):
  (let [node [[:a :foo]
              [:b
               [:a :bar]
               [:c :baz]]
              [:c :qux]]
        ; Let's say branches are things with more vectors in them
        children (fn [node]
                   (when (some vector? node)
                     (filter vector? node)))]
    (loop [; Add this initial node
           frontier (-> (gs/dfs) (gs/add-children [node]))]
      (let [; Get the next node and new frontier
            [node frontier] (gs/first+rest frontier)]
        ; Return nil if no more nodes
        (when node
          (println node)
          ; Add children to the frontier and loop
          (recur (gs/add-children frontier (children node))))))))

;; Use the SearchStrategy protocol to implement your own algorithm
(defrecord A*Search [children heuristic]
  gs/SearchStrategy
  ; Get the first node and the rest of the "frontier"
  (first+rest [_]
    [(:node (first children))
     (->A*Search (rest children) heuristic)])
  ; Add the children to the frontier (or not, see gully.strategy/sbd)
  ; This is how you specify the order that nodes are searched
  (add-children [this new-children]
    (update this :children #(->> new-children
                                 (map (fn [child]
                                        ; NOTE: You can use the functions in gully.core to interact with nodes
                                        ; e.g. (g/path child) => [:a :b]
                                        {:score (heuristic child)
                                         :node child}))
                                 (concat %)
                                 (sort-by :score)))))

(defn a* [heuristic] (->A*Search [] heuristic))



;; >> Example usage

(def a? :a)
(def b? :b)
(def c? :c)

(defn score-node [node]
  (let [data (if (g/branch? node)
               (g/data node)
               (let [endpoint (g/endpoint node)]
                 (when (map? endpoint)
                   endpoint)))]
    (get data :score 999)))

(def app
  (g/router
    [[a? :foo]
     [b? {:score 1}
      [a? {:score 1
           :endpoint :bar}]]
     [c? :baz]]
    {; Use :log? to log out each node as it's searched (great for debugging)
     :log? true
     :strategy (a* score-node)}))

(comment
  ; Usually this would return :foo but with a* it knows to search
  ; the cheaper [b a] path first.
  (app {:b 1 :a 1})
  ; (out) {:path [], :children (...)}
  ; (out) {:path [nil],    :predicate :b, :data {:score 1}, :children (...)}
  ; (out) {:path [nil :b], :predicate :a, :endpoint {:score 1, :endpoint :bar}, :data [{:score 1}]}
  ; => {:predicate :a,
  ;     :path [nil :b],
  ;     :endpoint {:score 1, :endpoint :bar},
  ;     :data [{:score 1}]

  ; Otherwise it does normal(ish) bfs as all paths are equally expensive
  (app {:c 1})
  ; (out) {:path [], :children (...)}
  ; (out) {:path [nil], :predicate :b, :data {:score 1}, :children (...)}
  ; (out) {:path [nil], :predicate :a, :endpoint :foo}
  ; (out) {:path [nil], :predicate :c, :endpoint :baz}
  ; => {:predicate :c,
  ;     :path [nil],
  ;     :endpoint :baz}
  ,)
