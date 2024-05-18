(ns gully.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gully.core :as g]
            [gully.strategy :as s]
            [gully.parse :as p])
  (:import (clojure.lang ExceptionInfo)))

;; What are the features of gully?
;; - Select an endpoint based on predicates
;;   - Can fail to build a router
;; - Efficient
;;   - Only calls each predicate at most once
;; - Optionally lazy
;;   - Can be turned on to find all matches
;;   - Only continues searching when the next element is requested
;; - Pluggable search algorithms
;;   - Users can use built in:
;;     - dfs, bfs, sbd
;;   - Users can write their own
;; - Node conforming
;;   - Edit the predicate
;;   - Can see the whole node
;;     - For branches you could edit:
;;       - predicate, data, children or path
;;     - For leaves you could edit:
;;       - predicate or endpoint, path or data (data-stack)
;;   - Happens *after* node construction
;;     - I.e. children first
;;     - I.e. path uses original predicate
;; - Path to node
;; - Stack of data from branch nodes
;; - Optional logging
;; - Functions for interacting with nodes

(deftest compile-test
  (testing "leaf"
    (let [app (g/router [:a :foo])]
      (is (= {:predicate :a
              :path []
              :endpoint :foo}
             (app {:a 1})
             (app {:a 2})))
      (is (nil? (app {})))))
  (testing "branch"
    (testing "single nested route"
      (let [app (g/router [[:a :foo]])]
        (is (= {:predicate :a
                :path [nil]
                :endpoint :foo}
               (app {:a 1})
               (app {:a 2})))
        (is (nil? (app {})))))
    (testing "single nested route w/ predicate"
      (let [app (g/router [:b
                           [:a :foo]])]
        (is (= {:predicate :a
                :path [:b]
                :endpoint :foo}
               (app {:b 1 :a 1})
               (app {:b 2 :a 2})))
        (is (nil? (app {})))
        (is (nil? (app {:a 1})))))
    (testing "multiple nested routes"
      (let [app (g/router [[:a :foo]
                           [:b
                            [:a :unreachable]
                            [:c :bar]]
                           [:c :baz]])]
        (is (nil? (app {})))
        (is (= {:predicate :a
                :path [nil]
                :endpoint :foo}
               (app {:a 1})
               (app {:b 1 :a 1})))
        (is (= {:predicate :c
                :path [nil :b]
                :endpoint :bar}
               (app {:b 1 :c 1})))
        (is (= {:predicate :c
                :path [nil]
                :endpoint :baz}
               (app {:c 1}))))))
  (testing "error"
    (is (thrown? ExceptionInfo
          (g/router {:data 1})))
    (is (thrown? ExceptionInfo
          (g/router [:b 
                     [:a :foo]
                     {:data 1}])))))

(defn count-calls [f]
  (let [calls (atom 0)]
    [calls
     (fn [& args]
       (swap! calls inc)
       (apply f args))]))

(deftest count-calls-test
  (let [yes (constantly true)
        [calls yes] (count-calls yes)]
    (is (= 0 @calls))
    (is (yes))
    (is (= 1 @calls))
    (is (yes))
    (is (= 2 @calls))))

(deftest options-test
  (testing "all-results"
    (let [app (g/router [[:a :foo]
                         [:b
                          [:a :bar]]
                         [:c :baz]
                         [:a :qux]
                         [:d :quux]]
                        {:all-results true})]
      (is (= [{:predicate :a
               :path [nil]
               :endpoint :foo}
              {:predicate :a
               :path [nil :b]
               :endpoint :bar}
              {:predicate :a
               :path [nil]
               :endpoint :qux}]
             (app {:b 1 :a 1}))))
    (testing "lazy"
      (let [yes (constantly true)
            [calls-yes-1 yes-1] (count-calls yes)
            [calls-yes-2 yes-2] (count-calls yes)
            app (g/router [[yes-1 :foo]
                           [yes-2 :bar]]
                          {:all-results true})]
        (is (= 0 @calls-yes-1))
        (is (= 0 @calls-yes-2))
        (testing "Don't realise the result"
          (app {})
          (is (= 0 @calls-yes-1))
          (is (= 0 @calls-yes-2)))
        (testing "Realise only the first result"
          (first (app {}))
          (is (= 1 @calls-yes-1))
          (is (= 0 @calls-yes-2)))
        (testing "Realise all results"
          (doall (app {}))
          (is (= 2 @calls-yes-1))
          (is (= 1 @calls-yes-2))))))

  ;; Is this too brittle?
  (testing "log?"
    (let [app (g/router [[:a :foo]
                         [:b
                          [:a :bar]]
                         [:c :baz]]
                        {:log? true})
          out (with-out-str (app {:c 1}))
          split (str/split out #"\n")]
      ;; four lines:
      (testing "first"
        (is (re-find #":path \[\]" (nth split 0))))
      (testing "second"
        (is (= "{:predicate :a, :path [nil], :endpoint :foo}"
               (nth split 1))))
      (testing "third"
        (is (re-find #":path \[nil\]" (nth split 2)))
        (is (re-find #":path \[nil :b\]" (nth split 2))))
      (testing "fourth"
        (is (= "{:predicate :c, :path [nil], :endpoint :baz}"
               (nth split 3)))))))

(deftest pluggable-algs-test
  (testing "dfs"
    (let [app (g/router [[:b
                          [:b
                           [:b
                            [:a :deeper-but-first]]]]
                         [:b
                          [:a :shorter]]]
                        {:strategy (s/dfs)})]
      (is (= {:predicate :a
              :path [nil :b :b :b]
              :endpoint :deeper-but-first}
             (app {:b 1 :a 1})))))
  (testing "bfs"
    (let [app (g/router [[:b
                          [:b
                           [:b
                            [:a :deeper]]]]
                         [:b
                          [:a :shorter]]]
                        {:strategy (s/bfs)})]
      (is (= {:predicate :a
              :path [nil :b]
              :endpoint :shorter}
             (app {:b 1 :a 1})))))
  (testing "sbd"
    (let [app (g/router [[:b
                          [:a :foo]]
                         [:b
                          [:c :unreachable]]]
                        {:strategy (s/sbd)})]
      (is (nil? (app {:b 1})))
      (is (= {:predicate :a
              :path [nil :b]
              :endpoint :foo}
             (app {:b 1 :a 1})))
      (is (nil? (app {:b 1 :c 1}))))))

(defn conform-predicate [f]
  (fn [node]
    (let [old-pred (g/predicate node)]
      (-> node
          (update :predicate (fn [p] (when p (f p))))
          ; A little clunky :/
          (assoc :old-predicate old-pred)))))

(deftest conform-test
  ; Mostly covered in the parse tests
  (let [yes (constantly true)
        no (constantly false)
        app (g/router [[:no :foo]
                       [:yes :bar]
                       [:no :bar]]
                      {:conform (conform-predicate
                                  (fn [p]
                                    (case p
                                      :yes yes
                                      :no no)))})]
    (is (= {:predicate yes
            :old-predicate :yes
            :path [nil]
            :endpoint :bar}
           (app {})))))

(deftest route-data-test
  (let [app (g/router [{:data 2}
                       [{:data 3}
                        [:a :foo]]
                       [{:data 4}
                        [:c :bar]]]
                      {:data {:data 1}})]
    (is (= {:predicate :a
            :path [nil nil]
            :data [{:data 1} {:data 2} {:data 3}]
            :endpoint :foo}
           (app {:a 1})))))

(deftest node-api-test
  (testing "leaf"
    (let [parsed (p/parse [:a :foo])]
      (is (g/leaf? parsed))
      (is (not (g/branch? parsed)))
      (is (= :a (g/predicate parsed)))
      (is (= :foo (g/endpoint parsed)))))
  (testing "branch"
    (let [parsed (p/parse [:b {:data 1}
                           [:a :foo]])]
      (is (g/branch? parsed))
      (is (not (g/leaf? parsed)))
      (is (= :b (g/predicate parsed)))
      (is (= {:data 1} (g/data parsed)))
      (is (= [(p/parse [:a :foo] {:path [:b]
                                  :data-stack [{:data 1}]})]
             (g/children parsed))))))

(deftest one-call-per-predicate-test
  (let [[a-calls a] (count-calls :a)
        [b-calls b] (count-calls :b)
        [c-calls c] (count-calls :c)
        [d-calls d] (count-calls :d)
        [e-calls e] (count-calls :e)
        app (g/router [[a :foo]
                       [b
                        [c :bar]]
                       [a :baz]
                       [d :qux]
                       [e :quux]])]
    (is (= 0 @a-calls))
    (is (= 0 @b-calls))
    (is (= 0 @c-calls))
    (is (= 0 @d-calls))
    (is (= 0 @e-calls))
    (app {:d 1})
    (testing "at most called once"
      (is (= 1 @a-calls)))
    (is (= 1 @b-calls))
    (testing "sub tree not called"
      (is (= 0 @c-calls)))
    (is (= 1 @d-calls))
    (testing "not called after match"
      (is (= 0 @e-calls)))))
