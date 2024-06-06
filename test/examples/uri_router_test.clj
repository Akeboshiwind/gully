(ns examples.uri-router-test
  (:require [clojure.test :refer [deftest is testing]]
            [uri-router :as ur]))

(deftest non-existing-routes
  (let [app (ur/make-app)]
    (is (= (app {:uri "/api/does/not/exist"})
           {:status 404 :body "Not found"}))
    (is (= (app {:uri "/api/user/no-route"})
           {:status 404 :body "Not found"}))))

(deftest user-route
  (let [app (ur/make-app)]
    (testing "Getting a user"
      (is (= (app {:uri "/api/user"})
             {:status 404 :body "User not found"}))
      (is (= (app {:uri "/api/user" :params {:id 1}})
             {:status 200 :body {:name "Oliver"}})))

    (testing "Posting a new user"
      (is (= (app {:uri "/api/user" :params {:id 4}})
             {:status 404 :body "User not found"}))
      (is (= (app {:request-method :post
                   :uri "/api/user"
                   :params {:user {:name "Alice"}}})
             {:status 200 :body 4}))
      (is (= (app {:uri "/api/user" :params {:id 4}})
             {:status 200 :body {:name "Alice"}})))))

(deftest books-route
  (let [app (ur/make-app)]
    (testing "Getting books"
      (is (= (app {:uri "/api/books"})
             {:status 200,
              :body {1 {:title "Uprooted"},
                     2 {:title "The Golem and the Jinni"}}})))
    (testing "Doesn't support post"
      (is (= (app {:request-method :post :uri "/api/books"})
             {:status 404 :body "Not found"})))))

(deftest static-page
  (let [app (ur/make-app)]
    (is (= (app {:uri ""})
           {:status 200 :body "My static page"}))
    (is (= (app {:uri "/home"})
           {:status 200 :body "My static page"}))))

(deftest nested-leaf-route
  (let [app (ur/router
              ["/branch"
               ["/leaf" (constantly (ur/ok "leaf"))]])]
    (is (= (app {:uri "/branch/leaf"})
           {:status 200 :body "leaf"}))))
