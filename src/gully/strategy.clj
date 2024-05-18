(ns gully.strategy)

(defprotocol SearchStrategy
  ; This could have been two functions but I wanted to support the case when
  ; `first` isn't stateless.
  ; e.g. If `first` picked a random child it would be difficult to implement `rest`
  ; Could probably not have bothered
  (first+rest [this] "Returns a tuple of the first node and the rest of the nodes")
  (add-children [this children] "Add children to the frontier"))
; See the a* example for how you might use this protocol to do a simple search
; It might help you understand why the protocol is defined this way

(defrecord DepthFirstSearch [children]
  SearchStrategy
  (first+rest [_]
    [(first children)
     (->DepthFirstSearch (rest children))])
  (add-children [this new-children]
    (update this :children #(concat new-children %))))

(defn dfs [] (->DepthFirstSearch []))

(defrecord BreadthFirstSearch [children]
  SearchStrategy
  (first+rest [_]
    [(first children)
     (->BreadthFirstSearch (rest children))])
  (add-children [this new-children]
    (update this :children #(concat % new-children))))

(defn bfs [] (->BreadthFirstSearch []))

(defrecord SingleBranchDescent [children]
  SearchStrategy
  (first+rest [_]
    [(first children)
     (->SingleBranchDescent (rest children))])
  (add-children [this new-children]
    (assoc this :children new-children)))

(defn sbd [] (->SingleBranchDescent []))
