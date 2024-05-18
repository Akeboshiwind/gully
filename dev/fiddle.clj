(ns fiddle
  (:require [gully.core :as g]
            [criterium.core :as c]))

(comment
  ;; What *is* the max recursion depth?
  (let [yes (constantly true)
        n 30000]
    (loop [node [yes :foo]
           max-depth n]
      (when-not (zero? max-depth)
        (when (zero? (mod max-depth 1000))
          (println max-depth))
        (if (try
              (let [app (g/router node)]
                (try
                  (app {})
                  (catch StackOverflowError e
                    (println "app threw")
                    (throw e))))
              true
              (catch StackOverflowError _
                false))
          (recur [node] (dec max-depth))
          ; Count of brackets
          (inc (- n max-depth)))))))
  ; => 5136
  ; Seems to change with the implementation of search
  ; And the app didn't throw, the router did which is nice
  ; Anyway it's large enough so it's fine

(comment
  ;; More bad benchmarks
  (defn random-ctx []
    (merge
      (when (rand-nth [true false])
        {:a 1})
      (when (rand-nth [true false])
        {:a 2})
      (when (rand-nth [true false])
        {:b 1})
      (when (rand-nth [true false])
        {:c 1})))

  (def random-ctxs (take 1000 (repeatedly random-ctx)))

  (defn a1? [ctx] (= 1 (:a ctx)))
  (defn a2? [ctx] (= 2 (:a ctx)))
  (def b? :b)
  (def c? :c)

  (def route
    [[a1? :foo]
     [b?
      [a1? :unreachable]
      [a2? :bar]]
     [a2? :baz]
     [b?
      [c? :qux]]
     [c? :quux]])

  (let [app (g/router route)]
    (c/with-progress-reporting
      (c/quick-bench
        (doseq [ctx random-ctxs]
          (app ctx))
        :verbose)))
  ; (out) aarch64 Mac OS X 14.2.1 8 cpu(s)
  ; (out) OpenJDK 64-Bit Server VM 21.0.2+13-LTS
  ; (out) Runtime arguments: -XX:-OmitStackTraceInFastThrow -Dclojure.basis=.cpcache/3166878625.basis
  ; (out) Evaluation count : 13140 in 6 samples of 2190 calls.
  ; (out)       Execution time sample mean : 45.687419 µs
  ; (out)              Execution time mean : 45.687419 µs
  ; (out) Execution time sample std-deviation : 553.908756 ns
  ; (out)     Execution time std-deviation : 573.480296 ns
  ; (out)    Execution time lower quantile : 45.189645 µs ( 2.5%)
  ; (out)    Execution time upper quantile : 46.488672 µs (97.5%)
  ; (out)                    Overhead used : 1.679654 ns

  ;; Not quite the same
  (defn manual [ctx]
    (cond
      (a1? ctx) {:endpoint :foo
                 :path [nil a1?]}
      (and (b? ctx) (a1? ctx)) {:endpoint :unreachable
                                :path [nil b? a1?]}
      (and (b? ctx) (a2? ctx)) {:endpoint :bar
                                :path [nil b? a2?]}
      (a2? ctx) {:endpoint :baz
                 :path [nil a2?]}
      (and (b? ctx) (c? ctx)) {:endpoint :qux
                               :path [nil b? c?]}
      (c? ctx) {:endpoint :qux
                :path [nil b? c?]}
      :else nil))

  (c/with-progress-reporting
    (c/quick-bench
      (doseq [ctx random-ctxs]
        (manual ctx))
      :verbose))
  ; (out) aarch64 Mac OS X 14.2.1 8 cpu(s)
  ; (out) OpenJDK 64-Bit Server VM 21.0.2+13-LTS
  ; (out) Runtime arguments: -XX:-OmitStackTraceInFastThrow -Dclojure.basis=.cpcache/3166878625.basis
  ; (out) Evaluation count : 10878 in 6 samples of 1813 calls.
  ; (out)       Execution time sample mean : 61.672464 µs
  ; (out)              Execution time mean : 61.672464 µs
  ; (out) Execution time sample std-deviation : 3.756384 µs
  ; (out)     Execution time std-deviation : 3.829573 µs
  ; (out)    Execution time lower quantile : 57.588020 µs ( 2.5%)
  ; (out)    Execution time upper quantile : 66.898670 µs (97.5%)
  ; (out)                    Overhead used : 1.679654 ns

  ; That doesn't seem right? Maybe because I do fewer checks?
  ,)
