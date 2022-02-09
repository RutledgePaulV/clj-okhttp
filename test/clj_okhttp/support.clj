(ns clj-okhttp.support
  (:require [clojure.test :refer :all])
  (:import [org.testcontainers.containers GenericContainer]
           [org.testcontainers.containers.wait.strategy HttpWaitStrategy]))


(defn start-httpbin []
  (doto (GenericContainer. "kennethreitz/httpbin:latest")
    (.withExposedPorts (into-array Integer [(int 80)]))
    (.setPortBindings ["8123:80"])
    (.waitingFor (doto (HttpWaitStrategy.)
                   (.forPath "/get")
                   (.forPort 80)
                   (.withMethod "GET")
                   (.forStatusCode 200)))
    (.start)))

(defn start-echo-server []
  (doto (GenericContainer. "jmalloc/echo-server:latest")
    (.withExposedPorts (into-array Integer [(int 8080)]))
    (.setPortBindings ["8124:8080"])
    (.waitingFor (doto (HttpWaitStrategy.)
                   (.forPath "/get")
                   (.forPort 8080)
                   (.withMethod "GET")
                   (.forStatusCode 200)))
    (.start)))

(defonce test-httpbin
  (delay (start-httpbin)))

(defonce test-echo-server
  (delay (start-echo-server)))

(defn get-base-url
  ([] (get-base-url (force test-httpbin)))
  ([^GenericContainer container]
   (str "http://"
        (.getContainerIpAddress container)
        ":"
        (.getMappedPort container 80))))

(defn get-base-wss-url
  ([] (get-base-wss-url (force test-echo-server)))
  ([^GenericContainer container]
   (str "http://"
        (.getContainerIpAddress container)
        ":"
        (.getMappedPort container 8080))))
