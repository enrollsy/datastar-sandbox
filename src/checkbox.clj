(ns checkbox
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [dev.onionpancakes.chassis.core :as c]
    [dev.onionpancakes.chassis.compiler :as cc]
    [org.httpkit.server :as http.server]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit2 :as hk])
  (:import [java.time Instant]))

(defn log
  ([id] (log id {}))
  ([id data]
   (clojure.pprint/pprint (assoc data :id id :time (str (Instant/now))))))

(defn shim [& {:keys [inspector? cdn?] :or {cdn? true}}]
  (cc/compile
    [c/doctype-html5
     [:html
      [:head {:lang "en-US"}
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]]
      [:body
       [:style "
        main {
          display: flex;
          flex-direction: row;
          justify-content: center;
        }
        section {
          display: flex;
          flex-direction: column;
          width: 150px;
        }
        "]
       [:div {:data-init "@post(window.location.pathname + window.location.search)"}
        [:main#main]]
       (when inspector? [:datastar-inspector])
       [:script {:type "module" :src (if cdn? d*/CDN-url "/datastar.js")}]
       (when inspector? [:script {:type "module" :src "/datastar-inspector.js"}])]]]))

(defonce state (atom {:a true :b true :c true}))

(defn toggle [{:keys [query-string] :as request}]
  (let [params (->> (str/split query-string #"&")
                    (map #(let [[k v] (str/split % #"=")]
                            [(keyword k) (keyword v)]))
                    (into {}))]
    (when (:checkbox params)
      (swap! state update (:checkbox params) not)))
  (response (c/html (main request))))

(defn checkboxes [request]
  (cc/compile
    [:section
     [:div
      [:input {:id "a" :type "checkbox" :checked (:a @state) :data-on:click "@post('/toggle?checkbox=a')"}]
      [:label {:for "a"} "A"]]
     [:div
      [:input {:id "b" :type "checkbox" :checked (:b @state) :data-on:click "@post('/toggle?checkbox=b')"}]
      [:label {:for "b"} "B"]]
     [:div
      [:input {:id "c" :type "checkbox" :checked (:c @state) :data-on:click "@post('/toggle?checkbox=c')"}]
      [:label {:for "c"} "C"]]]))

(defn main [request]
  (cc/compile
    [:main#main
     (checkboxes request)
     (checkboxes request)]))

(defn response [body]
  {:status 200 :headers {"content-type" "text/html"} :body body})

(defn handler [{:keys [request-method uri] :as request}]
  (case uri
    "/" (if (= request-method :get)
          (response (c/html (shim)))
          (response (c/html (main request))))
    "/toggle" (toggle request)
    "/datastar.js" (response (io/file (io/resource "datastar.js")))
    "/datastar-inspector.js" (response (io/file (io/resource "datastar-inspector.js")))
    {:status 404}))

(comment
  (def server
    (http.server/run-server
      (fn [request] (handler request))
      {:legacy-return-value? false
       :server-header nil
       :port 9002
       :graceful-stop-millis 5000}))

  (deref (http.server/server-stop! server))
  )
