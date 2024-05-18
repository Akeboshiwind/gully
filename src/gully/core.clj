(ns gully.core
  (:require [gully.parse :as p]
            [gully.strategy :as s]))

(def predicate :predicate)
(def children :children)
(def data :data)
(def path :path)
(def endpoint :endpoint)

(defn branch? [node] (boolean (:children node)))
(def leaf? (complement branch?))

(defn- log-node [node f]
  (fn [ctx]
    (println node)
    (f ctx)))

(defn- compile'
  [frontier known-state {:keys [all-results log?] :as opts}]
  (let [[node frontier] (s/first+rest frontier)]
    (cond
      ; No next node, so stop
      (nil? node) (constantly nil)

      (branch? node)
      (let [pred (predicate node)
            ; Can be one of three values: nil, true & false
            state (known-state pred)
            ; Run only when we know the branch is a match
            when-pass (-> frontier
                          ; Only add the children when we know the check passed
                          (s/add-children (children node))
                          (compile' (if pred
                                      ; Tell the next node we know the result
                                      (assoc known-state pred true)
                                      known-state)
                                    opts))
            ; Run only when we know the branch is not a match
            when-fail (compile' frontier
                                (if pred
                                  (assoc known-state pred false)
                                  known-state)
                                opts)
            result-fn (cond
                        ; Skip the check if no predicate
                        (nil? pred) when-pass
                        ; Skip the check if we already know the result
                        (not (nil? state)) (if state when-pass when-fail)
                        ; Otherwise do the check
                        :else (fn [ctx]
                                (if (pred ctx)
                                  (when-pass ctx)
                                  (when-fail ctx))))]
        (if log?
          (log-node node result-fn)
          result-fn))

      #_{:clj-kondo/ignore [:cond-else]}
      :leaf
      (let [pred (predicate node)
            ; Can be one of three values: nil, true & false
            state (known-state pred)
            when-pass (compile' frontier
                                (assoc known-state pred true)
                                opts)
            when-fail (compile' frontier
                                (assoc known-state pred false)
                                opts)
            result-fn (cond
                        ; Skip the check if we already know the result
                        (not (nil? state))
                        (if state
                          (if all-results
                            (fn [ctx]
                              (cons node (lazy-seq (when-pass ctx))))
                            node)
                          ; Skip the node if we know it's not a match
                          when-fail)

                        ; Otherwise do the check:
                        all-results
                        (fn [ctx]
                          (if (pred ctx)
                            (cons node (lazy-seq (when-pass ctx)))
                            (when-fail ctx)))
                        :else
                        (fn [ctx]
                          (if (pred ctx)
                            node
                            (when-fail ctx))))]
        (if log?
          (log-node node result-fn)
          result-fn)))))

(defn router
  "Create a `router` from a `route`.

  # Options:
  - `:data`        - Data that is added to every match.
  - `:strategy`    - The strategy to use for searching. (default: `dfs`)
  - `:all-results` - Instead return to instead be a lazy-seq of all matches.
                     NOTE: Changes return type!
  - `:conform`     - A function that transforms a `parsed node` before it is searched.
                     Allows you to transform the predicate before it is called, or the whole node that's returned.
  - `:log?`        - Log the node as it's searched.

  # Definitions:
  A router is a function that takes a context (could be a map, could be anything)
  and returns the first `match` found.
  A route is either a `leaf` or a `branch`.
  A leaf is a vector of a predicate and an endpoint, like so:
  `[:a :foo]`
  A branch is a vector of a predicate and data with one or `routes` as children.
  `[:b {:data 1} ; Both predicate and data are optional
    [:a :foo]]`
  A match is a map of:
  {:predicate <predicate> ; The final predicate that was matched
   :path [<path>]         ; The path of predicates that were matched (pre-conform)
   :endpoint <endpoint>   ; The endpoint on the leaf that was matched
   :data [<data>]}        ; A vector of any data that was picked up from branches, in order

  A parsed node is either:
  For a branch node:
  {:predicate <predicate> ; optional
   :data      <data>      ; optional
   :children  [<children>]
   :path      [<path>]}
  For a leaf node:
  {:predicate <predicate>
   :path      [<path>]
   :data      <data>}     ; optional
  There are some functions in gully.core to access these as well as tell these appart."
  ([routes]
   (router routes {}))
  ([routes {:keys [conform] :as opts
            :or {conform identity}}]
   (let [opts (merge {:strategy (s/dfs)}
                     opts)
         ; We split the parsing into it's own function to simplify the
         ; compilation step above.
         ; NOTE: This does mean we walk the tree twice though!
         routes (p/parse routes
                         (cond-> {:conform conform}
                           (:data opts) (assoc :data-stack [(:data opts)])))
         frontier (-> (:strategy opts)
                      (s/add-children [routes]))
         compiled (compile' frontier {} opts)]
     (if (:all-results opts)
       (fn [ctx]
         (lazy-seq
           (compiled ctx)))
       compiled))))

(comment
  (def a1? #(= 1 (:a %)))
  (def a2? #(= 2 (:a %)))
  (def b? :b)
  (def c? :c)

  (let [app (router [[:a1? :foo]
                     [:b?
                      [:a1? :bar]
                      [:a2? :baz]]
                     [:a2? :qux]
                     [:b?
                      [:c? :quux]]]
                    {:conform
                     (fn [node]
                       (update node :predicate (fn [p]
                                                 (when p
                                                   (case p
                                                     :a1? a1?
                                                     :a2? a2?
                                                     :b? b?
                                                     :c? c?)))))})]
    (app {:b 1 :a 2}))
  ;; Each predicate is only called a maximum once
  ,)
