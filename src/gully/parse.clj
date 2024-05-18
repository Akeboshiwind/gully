(ns gully.parse)

(defn branch? [node]
  (some vector? node))

(defn predicate? [p]
  (not (or (vector? p)
           (map? p))))

(defn children [node]
  (if (predicate? (first node))
    (if (map? (second node))
      (drop 2 node)
      (drop 1 node))
    (if (map? (first node))
      (drop 1 node)
      node)))

(defn predicate [node]
  (let [p (first node)]
    (if (predicate? p)
      p
      nil)))

(defn data [node]
  (if (predicate? (first node))
    (when (map? (second node))
      (second node))
    (when (map? (first node))
      (first node))))

(defn parse
  ([node]
   (parse node {}))
  ([node {:keys [conform path data-stack]
          :or {conform identity
               path []
               data-stack []}
          :as opts}]
   (if (not (vector? node))
     (throw (ex-info "Unexpected endpoint" {:node node}))
     ; Conform is run *after* the node *and* it's children are parsed
     (conform
       (if (branch? node)
         (let [p (predicate node)
               d (data node)
               new-path (conj path p) 
               new-data-stack (if d
                                (conj data-stack d)
                                data-stack)]
           (cond-> {:path path
                    :children
                    ; Realise the children otherwise you can end up with weird
                    ; errors when trying to print a result with an unexpected endpoint
                    (mapv #(parse % (-> opts
                                        (assoc :path new-path)
                                        (assoc :data-stack new-data-stack)))
                          (children node))}
             p (assoc :predicate p)
             d (assoc :data d)))
         ; leaf
         (let [[p endpoint] node]
           (cond-> {:predicate p
                    :path path
                    :endpoint endpoint}
             (seq data-stack) (assoc :data data-stack))))))))

(comment
  (parse [[:a1 :foo]
          [:b
           [:a1 :bar]
           [:a2 :baz]]
          [:a2 :qux]
          [:b
            [:c :quux]]])

  (parse [[[[:a1 :foo]]]])

  (parse [:a1 :foo])

  (parse {:test :this})

  ,)
