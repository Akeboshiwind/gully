(ns benchmark
  "An insufficient benchmark to compare Gully with Reitit."
  (:require [reitit.core :as r]
            [gully.core :as g]
            [gully.strategy :as gs]
            [clojure.string :as str]
            [criterium.core :as c]))

(def reitit-router
  (r/router
    [["/api"
      ["/user" {:get :get-user
                :post :add-user}]
      ["/books" {:get :get-books}]]
     ; I could have sworn that reitit didn't work like this
     ["/api*" :404]
     ["*" :spa]]
    {:conflicts false}))

(def paths
  ["/api/user"
   "/api/books"
   "/api/something"
   "/api/else"
   "/my-page"])

(comment
  (c/with-progress-reporting
    (c/bench
      (doseq [path paths]
        (r/match-by-path reitit-router path))
      :verbose))
  ; (out) aarch64 Mac OS X 14.2.1 8 cpu(s)
  ; (out) OpenJDK 64-Bit Server VM 21.0.2+13-LTS
  ; (out) Runtime arguments: -XX:-OmitStackTraceInFastThrow -Dclojure.basis=.cpcache/3166878625.basis
  ; (out) Evaluation count : 203179020 in 60 samples of 3386317 calls.
  ; (out)       Execution time sample mean : 302.972875 ns
  ; (out)              Execution time mean : 303.486954 ns
  ; (out) Execution time sample std-deviation : 23.788960 ns
  ; (out)     Execution time std-deviation : 24.384372 ns
  ; (out)    Execution time lower quantile : 290.065159 ns ( 2.5%)
  ; (out)    Execution time upper quantile : 356.637293 ns (97.5%)
  ; (out)                    Overhead used : 1.679654 ns
  ; (out) 
  ; (out) Found 10 outliers in 60 samples (16.6667 %)
  ; (out) 	low-severe	 3 (5.0000 %)
  ; (out) 	low-mild	 7 (11.6667 %)
  ; (out)  Variance from outliers : 60.1327 % Variance is severely inflated by outliers
  ,)

; Copied from the examples
(defn conform-predicate
  "Return a function that `conform`s the predicate on a node with the given
  function."
  [f]
  (fn [node]
    (-> node
        (update :predicate (fn [p]
                             (when p
                               (f p node)))))))

(defn ->url-matcher
  "Combine the path and current predicate into a regex-matcher to match the
  current uri."
  [p node]
  (if (string? p)
    (let [path (g/path node)
          leaf? (or (g/leaf? node)
                    (every? (every-pred (comp fn? g/predicate) g/leaf?)
                            (g/children node)))
          uri-so-far (str/join (conj path p))
          patt (re-pattern
                 (str "^" uri-so-far (when leaf? "$")))]
      (fn [{:keys [uri]}]
        (re-find patt uri)))
    p))

(defn uri-router [routes]
  (g/router routes {:conform (conform-predicate ->url-matcher)
                    :strategy (gs/sbd)}))

(def gully-router
  (uri-router
    [["/api"
      ["/user" {:get :get-user
                :post :add-user}]
      ["/books" {:get :get-books}]]
     [".*" :spa]]))

(comment
  (c/with-progress-reporting
    (c/bench
      (doseq [path paths]
        (gully-router {:uri path}))
      :verbose))
  ; (out) aarch64 Mac OS X 14.2.1 8 cpu(s)
  ; (out) OpenJDK 64-Bit Server VM 21.0.2+13-LTS
  ; (out) Runtime arguments: -XX:-OmitStackTraceInFastThrow -Dclojure.basis=.cpcache/3166878625.basis
  ; (out) Evaluation count : 137722320 in 60 samples of 2295372 calls.
  ; (out)       Execution time sample mean : 446.761257 ns
  ; (out)              Execution time mean : 446.687460 ns
  ; (out) Execution time sample std-deviation : 19.708415 ns
  ; (out)     Execution time std-deviation : 19.799352 ns
  ; (out)    Execution time lower quantile : 436.262720 ns ( 2.5%)
  ; (out)    Execution time upper quantile : 506.846773 ns (97.5%)
  ; (out)                    Overhead used : 1.679654 ns
  ; (out) 
  ; (out) Found 11 outliers in 60 samples (18.3333 %)
  ; (out) 	low-severe	 1 (1.6667 %)
  ; (out) 	low-mild	 10 (16.6667 %)
  ; (out)  Variance from outliers : 30.3375 % Variance is moderately inflated by outliers
  ,)

;; Whoa! We're only 1.5 times slower than Reitit!
;; Definitely these benchmarks are insufficient
