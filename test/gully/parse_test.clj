(ns gully.parse-test
  (:require [clojure.test :refer [deftest is testing]]
            [gully.parse :as p])
  (:import (clojure.lang ExceptionInfo)))

(deftest branch?-test
  (is (p/branch? [[:a :foo]]))
  (is (p/branch? [:b
                  [:a :foo]]))
  (is (p/branch? [{:data 1}
                  [:a :foo]]))
  (is (p/branch? [:b {:data 1}
                  [:a :foo]]))
  (is (not (p/branch? [:a :foo]))))

(deftest predicate?-test
  (is (p/predicate? :a))
  (is (p/predicate? #(> % 1)))
  (is (not (p/predicate? [:a :foo])))
  (is (not (p/predicate? {:data 1}))))

(deftest children-test
  (is (= (p/children [[:a :foo]
                      [:c :bar]])
         [[:a :foo]
          [:c :bar]]))
  (is (= (p/children [:b
                      [:a :foo]
                      [:c :bar]])
         [[:a :foo]
          [:c :bar]]))
  (is (= (p/children [{:data 1}
                      [:a :foo]
                      [:c :bar]])
         [[:a :foo]
          [:c :bar]]))
  (is (= (p/children [:b {:data 1}
                      [:a :foo]
                      [:c :bar]])
         [[:a :foo]
          [:c :bar]])))

(deftest predicate-test
  (testing "leaf"
    (is (= :a (p/predicate [:a :foo]))))
  (testing "branch"
    (is (nil? (p/predicate [[:a :foo]])))
    (is (= :b (p/predicate [:b [:a :foo]])))
    (is (nil? (p/predicate [{:data 1} [:a :foo]])))
    (is (= :b (p/predicate [:b {:data 1} [:a :foo]])))))

(deftest data-test
  (is (nil? (p/data [[:a :foo]])))
  (is (nil? (p/data [:b
                     [:a :foo]])))
  (is (= {:data 1} (p/data [{:data 1}
                            [:a :foo]])))
  (is (= {:data 1} (p/data [:b {:data 1}
                            [:a :foo]]))))

;; Are these too brittle?
(deftest parse-test
  (testing "leaf"
    (is (= {:predicate :a
            :path []
            :endpoint :foo}
           (p/parse [:a :foo])))
    (is (= {:predicate :a
            :path []
            :endpoint {:handler :foo}}
           (p/parse [:a {:handler :foo}]))))

  (testing "branch"
    (is (= {:path []
            :children [{:predicate :a
                        :path [nil]
                        :endpoint :foo}]}
           (p/parse [[:a :foo]])))
    (is (= {:predicate :b
            :path []
            :children [{:predicate :a
                        :path [:b]
                        :endpoint :foo}]}
           (p/parse [:b
                     [:a :foo]])))
    (is (= {:path []
            :data {:data 1}
            :children [{:predicate :a
                        :path [nil]
                        :data [{:data 1}]
                        :endpoint :foo}]}
           (p/parse [{:data 1}
                     [:a :foo]])))
    (is (= {:predicate :b
            :path []
            :data {:data 1}
            :children [{:predicate :a
                        :path [:b]
                        :data [{:data 1}]
                        :endpoint :foo}]}
           (p/parse [:b {:data 1}
                     [:a :foo]]))))

  (testing "error"
    (is (thrown? ExceptionInfo
          (p/parse {:test :this})))
    (is (thrown? ExceptionInfo
          (p/parse [:b
                    [:a :foo]
                    {:test :this}]))))

  ;; This feels like the weakest part of the library
  (testing "conform"
    (testing "can add items"
      (let [conform #(assoc % :test 1)]
        (is (= {:predicate :b
                :path []
                :test 1
                :children [{:predicate :a
                            :path [:b]
                            :endpoint :foo
                            :test 1}]}
               (p/parse [:b
                         [:a :foo]]
                        {:conform conform})))))
    (testing "can alter the predicate"
      (let [conform #(update % :predicate keyword)]
        (is (= {:predicate :b
                :path []
                :children [{:predicate :a
                            :path ['b]
                            :endpoint :foo}]}
               (p/parse '[b
                          [a :foo]]
                        {:conform conform}))))))

  (testing "data-stack"
    (is (= {:predicate :b
            :path []
            :data {:data 1}
            :children [{:predicate :a
                        :path [:b]
                        :data {:data 2}
                        :children [{:predicate :c
                                    :path [:b :a]
                                    :data [{:data 1} {:data 2}]
                                    :endpoint :foo}]}
                       {:predicate :c
                        :path [:b]
                        :data {:data 3}
                        :children [{:predicate :d
                                    :path [:b :c]
                                    :data [{:data 1} {:data 3}]
                                    :endpoint :bar}]}]}
           (p/parse [:b {:data 1}
                     [:a {:data 2}
                      [:c :foo]]
                     [:c {:data 3}
                      [:d :bar]]])))
    (testing "w/ initial"
      (is (= {:predicate :b
              :path []
              :data {:data 2}
              :children [{:predicate :a
                          :path [:b]
                          :data [{:data 1} {:data 2}]
                          :endpoint :foo}]}
             (p/parse [:b {:data 2}
                       [:a :foo]]
                      {:data-stack [{:data 1}]}))))))
