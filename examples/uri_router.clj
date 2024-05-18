(ns uri-router
  "An 'ok' uri router example."
  (:refer-clojure :exclude [get])
  (:require [clojure.string :as str]
            [gully.core :as g]
            [gully.strategy :as gs]))


;; >> Utils

(defn ok [body]
  {:status 200 :body body})

(defn not-found
  ([] (not-found "Not found"))
  ([body]
   {:status 404 :body body}))



;; >> Router

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

(defn chain-middleware
  "Combine a handler with middleware."
  [handler middleware]
  (reduce (fn [h m]
            (m h))
          handler
          middleware))

(defn router
  ([routes] (router routes nil))
  ([routes opts]
   (let [router (g/router routes
                          (merge opts
                                 {:conform (conform-predicate ->url-matcher)
                                  ; NOTE: We use single branch descent to ensure
                                  ;       ensure `/.*` doesn't match /api routes
                                  :strategy (gs/sbd)}))]
     (fn [request]
       (if-let [{:keys [endpoint data]} (router request)]
         ; NOTE: This would have been more efficient to do in `conform` but this
         ;       is simpler for demonstration purposes
         (let [data (if (map? endpoint) (conj data endpoint) data)
               ; NOTE: Keys on the outside take priority
               data (apply merge data)
               middleware (:middleware data)
               handler (if (map? endpoint) (:handler endpoint) endpoint)
               ; Add middleware to the selected handler
               handler (chain-middleware handler middleware)
               ; Add :data to the request
               request (assoc request :data data)]
           (handler request))
         (not-found))))))



;; >> App

(defn get
  "Test if the request is a GET request."
  [req]
  (or (nil? (:request-method req))
      (= :get (:request-method req))))
(defn post
  "Test if the request is a POST request."
  [req]
  (= :post (:request-method req)))

(def routes
  (router
    [["/api"
      ["/user"
       [get (fn [{:keys [store params]}]
              (let [id (:id params)
                    users (:user @store)]
                (if-let [user (clojure.core/get users id)]
                  (ok user)
                  (not-found "User not found"))))]
       [post (fn [{:keys [store params]}]
               (let [user (:user params)
                     get-max-id #(->> % :user keys (apply max))
                     new-store (swap! store
                                      (fn [store]
                                        (let [max-id (get-max-id store)]
                                          (assoc-in store [:user (inc max-id)] user))))]
                 (ok (get-max-id new-store))))]]
      ["/books"
       [get (fn [{:keys [store]}]
              (ok (:book @store)))]]]
     [".*" [get (fn [_] (ok "My static page"))]]]))

(def default-store
  {:user {1 {:name "Oliver"}
          2 {:name "Mary"}
          3 {:name "John"}}
   :book {1 {:title "Uprooted"}
          2 {:title "The Golem and the Jinni"}}})

(defn store-middleware [handler]
  (let [store (atom default-store)]
    (fn [request]
      (handler (assoc request :store store)))))

(def make-app #(-> routes store-middleware))



;; >> Usage

(comment
  (def app (make-app))

  ;; Make some requests
  (app {:request-method :get
        :uri "/api/books"})
  ; => {:status 200,
  ;     :body {1 {:title "Uprooted"},
  ;            2 {:title "The Golem and the Jinni"}})

  (app {:request-method :post
        :uri "/api/user"
        :params {:user {:name "Bob"}}})
  ; => {:status 200, :body 4}
  (app {:request-method :get
        :uri "/api/user"
        :params {:id 4}})
  ; => {:status 200, :body {:name "Bob"}}

  ; See the tests for more request examples
  ,)
