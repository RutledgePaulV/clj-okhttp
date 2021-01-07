(ns clj-okhttp.support
  (:require [clojure.test :refer :all])
  (:import [org.testcontainers.containers GenericContainer]
           [org.testcontainers.containers.wait.strategy HttpWaitStrategy]))


(defn start-httpbin []
  (doto (GenericContainer. "kennethreitz/httpbin:latest")
    (.setPortBindings ["8080:80"])
    (.waitingFor (HttpWaitStrategy.))
    (.start)))

(defonce test-httpbin
  (delay (start-httpbin)))

(defn get-base-url
  ([] (get-base-url test-httpbin))
  ([container]
   (str "http://"
        (.getContainerIpAddress (force container))
        ":"
        (.getMappedPort (force container) 80))))