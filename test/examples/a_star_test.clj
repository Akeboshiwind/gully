(ns examples.a-star-test
  (:require [clojure.test :refer [deftest is testing]]
            [a-star :as a*]
            [gully.parse :as p]))

(deftest score-node-test
  (testing "leaf"
    (is (= 999 (a*/score-node 
                 (p/parse
                   [:a :bar]))))
    (is (= 5 (a*/score-node 
               (p/parse
                 [:a {:score 5 :endpoint :bar}])))))
  (testing "leaf"
    (is (= 999 (a*/score-node 
                 (p/parse
                   [:b
                    [:a :bar]]))))
    (is (= 6 (a*/score-node 
               (p/parse
                 [:b {:score 6}
                  [:a :bar]]))))))

(deftest a-star-test
  ; Just to silence the prints
  (with-out-str
    (testing "First branch matches"
      (is (= (a*/app {:a 1})
             {:predicate :a
              :path [nil]
              :endpoint :foo})))
    (testing "Second branch matches due to score"
      (is (= (a*/app {:b 1 :a 1})
             {:predicate :a
              :path [nil :b]
              :endpoint {:score 1, :endpoint :bar}
              :data [{:score 1}]})))
    (testing "Still searches later branches"
      (is (= (a*/app {:c 1})
             {:predicate :c
              :path [nil]
              :endpoint :baz})))))
