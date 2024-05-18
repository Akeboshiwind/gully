# gully

A predicate routing library.

<p>
  <a href="#installation">Installation</a> |
  <a href="#getting-started">Getting Started</a> |
  <a href="/examples">examples</a>
</p>

> [!CAUTION]\
> Not considered stable just yet.
> I'll put a warning in the [changelog](/CHANGELOG.md) when a breaking change happens.
> This warning will be removed once I consider the API stable.


## Why

Sometimes you want routing but you're not in a web server.

```clojure
(require '[gully.core :as g])

(def a1? #(= 1 (:a %)))
(def a2? #(= 2 (:a %)))
(def b? :b)
(def c? :c)

(def routes
  (g/router
    [[a1? :foo]
     [b? {:other :data}
      [a1? :unreachable]
      [a2? :bar]]
     [[a2? :baz]]
     [{:data 1}
      [{:data 2}
       [c? :quix]]]]))

(routes {:a 1})
; => {:predicate a1?
;     :endpoint :foo
;     :path [nil]}
(routes {:b 1 :a 2})
; => {:predicate a2?
;     :endpoint :bar
;     :path [nil b?]
;     :data [{:other :data}]}
(routes {:a 2})
; => {:predicate a2?
;     :endpoint :baz
;     :path [nil]}
(routes {:c 1})
; => {:predicate c?
;     :endpoint :quix
;     :path [nil nil nil]
;     :data [{:data 1} {:data 2}]}
```

Mostly though I wrote this for use in [`tg-clj-server`](https://github.com/Akeboshiwind/tg-clj-server) 😅.
Maybe it'll be useful elsewhere?

A (basic) implementation of a normal uri router can be found [here](/examples/uri_router.clj).



## Installation

Use as a dependency in `deps.edn` or `bb.edn`:

```clojure
io.github.akeboshiwind/gully {:git/tag "v0.1.0" :git/sha "<todo>"}
```



## Getting Started

```clojure
(require '[gully.core :as g])
```

Create a routing function with `g/router`:
```clojure
(def routes
  (g/router
    [; leaf nodes are a vector of:
     ; - A predicate and
     ; - An "endpoint" (anything you want)
     [a1? :foo]
     ; branch nodes are a vector of:
     ; - An optional predicate
     ; - An optional map of data (must be a map)
     ; - One or more children (branches or leaves)
     [b? {:other :data}
      [a1? :unreachable]
      [a2? :bar]]]))
```

It is an error to do anything else:
```clojure
(g/router :foo)
(g/router [:b
           [:a :foo]
           ; Not a vector!
           :foo])
; => ExceptionInfo
```

Predicates act on the "context" which is passed to the generated route function:
```clojure
(routes {:b 1 :a 2})
; => {:predicate a2?
;     :endpoint :bar
;     :path [nil b?]
;     :data [{:other :data}]}
```

As you can see what's returned is:
- The predicate that was matched
- The "endpoint" of the matched node
- The path of predicates from branches that led to this match
- A list of the data found on any branches that led to this match
  - Returned in the order it was found (you can think of it like a stack)
  - If a branch has no data, nothing is added to the stack
  - You can specify an initial bit of data on `g/router` to apply to all branches

You can optionally ask to return a `lazy-seq` of all the results:

> [!CAUTION]
> This will change the return type to a lazy-seq!

```clojure
(def lazy-routes
  (g/router
    [[a1? :foo]
     [b? {:other :data}
      [a1? :bar]]
     [a1? :baz]]
    {:all-matches true}))
(routes {:b 1 :a 1})
; => ({:predicate a1?
;      :endpoint :foo
;      :path [nil]}
;     {:predicate a1?
;      :endpoint :bar
;      :path [nil b?]
;      :data [{:other :data}]}
;     {:predicate a1?
;      :endpoint :baz
;      :path [nil]})
```

What this is compiled down to is basically the minimum number of if's required to do the search (we assume that predicates are pure functions):
```clojure
; So this:
(g/router
  [[a1? :foo]
   [b?
    [a1? :unreachable]
    [a2? :bar]]
   [a2? :baz]
   [b?
    [c? :qux]]])

; compiles to (roughly):
(fn [ctx]
  (if (a1? ctx)
    {:predicate a1?
     :path [nil]
     :endpoint :foo}
    (if (:b ctx)
      ; Notice, no second check for `a1?`!
      ; If `a1?` is true, the first if would have caught it
      (if (a2? ctx)
        {:predicate a2?
         :path [nil :b]
         :endpoint :bar}
        (when (:c ctx)
          {:predicate :c
           :path [nil :b]
           :endpoint :qux}))
     ; Again, notice no check for `b?` or `c?`
     ; There's no need to check twice!
     (when (a2? ctx)
       {:predicate a2?
        :path [nil]
        :endpoint :baz}))))

; A tiny bit harder to read 😅
```

You can specify a different search algorithm with `:strategy`:
```clojure
(require '[gully.strategy :as gs])

; Breadth First Search
(def bfs-routes
  (g/router
    [[b?
      [b?
       [b?
        [a1? :deeper]]]]
     [b?
      [a1? :shallower]]]
    {:strategy (gs/bfs)}))

(bfs-routes {:b 1 :a 1})
; => {:predicate a1?
;     :endpoint :shallower
;     :path [nil b?]}
```

You can also create your own search algorithms! See the [A*](/examples/a_star.clj) example.

Finally you can specify a `:conform` function to modify a node before it is searched:
```clojure
(defn conform-predicate [f]
  (fn [node]
    ; Nodes at the conform step are maps of either:
    ; - For branches:
    ;   - :predicate (optional)
    ;   - :data (optional)
    ;   - :children
    ;   - :path
    ; - For leaves:
    ;   - :predicate
    ;   - :endpoint
    ;   - :path
    ; NOTE: There are some functions in `gully.core` for testing if a nodes is
    ;       a leaf or branch and getting these bits of data.
    (update node :predicate #(when % (f %)))))

; By conforming strings/keywords to predicates you can create your own DSL!
(defn command->predicate [p]
  (if (string? p)
    (let [patt (re-pattern p)]
      (fn [ctx]
        (re-find patt (:message ctx))))
    p))

(defn admin? [ctx]
  (:is-admin? ctx))

(def conform-routes
  (g/router
    [[admin?
      ["/private" :private-handler]]
     ["/public" :public-handler]]
    {:conform (conform-predicate command->predicate)}))

(conform-routes
  {:message "/public"})
; => {:predicate <wrapped-function>
;     :endpoint :public-handler
;     :path [nil]}
(conform-routes
  {:message "/private"})
; => nil
(conform-routes
  {:is-admin true
   :message "/private"})
; => {:predicate <wrapped-function>
;     :endpoint :private-handler
;     :path [nil admin?]}
```



## Releasing

1. Tag the commit `v<version>`
2. `git push --tags`
3. Update the README.md with the new version and git hash
4. Update the CHANGELOG.md
