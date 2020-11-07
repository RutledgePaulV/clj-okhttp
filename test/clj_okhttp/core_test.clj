(ns clj-okhttp.core-test
  (:require [clojure.test :refer :all]
            [clj-okhttp.core :refer :all]
            [muuntaja.core :as m])
  (:refer-clojure :exclude [get])
  (:import [okhttp3 Response]))

(defonce test-client (create-client))


(deftest format-request-and-response-test

  (testing "json request encoded by malli as byte array input stream"
    (let [input-stream-body
          {:body     {:test "stuff"}
           :muuntaja (assoc m/default-options :return :input-stream)
           :headers  {"content-type" "application/json"}}
          response
          (patch test-client "https://httpbin.org/patch" input-stream-body)]
      (is (= 200 (:status response)))
      (is (= {:test "stuff"} (get-in response [:body :json])))))

  (testing "edn request encoded by malli as output stream callback"
    (let [output-stream-body
          {:body     {:test "stuff"}
           :muuntaja (assoc m/default-options :return :output-stream)
           :headers  {"content-type" "application/edn"}}
          response
          (patch test-client "https://httpbin.org/patch" output-stream-body)]
      (is (= 200 (:status response)))
      (is (= (pr-str {:test "stuff"}) (get-in response [:body :data])))))

  (testing "transit request encoded by malli as byte array"
    (let [bytes-body
          {:body     {:test "stuff"}
           :muuntaja (assoc m/default-options :return :bytes)
           :headers  {"content-type" "application/transit+json"}}
          response
          (patch test-client "https://httpbin.org/patch" bytes-body)]
      (is (= 200 (:status response)))
      (is (= ["^ " "~:test" "stuff"] (get-in response [:body :json]))))))


(deftest basic-auth
  (let [request  {:basic-auth ["user" "password"]}
        response (get test-client "https://httpbin.org/basic-auth/user/password" request)]
    (is (= 200 (:status response)))
    (is (= {:authenticated true :user "user"} (:body response)))))


(deftest websocket-test
  (let [upgrade-request
        {:url "https://echo.websocket.org" :request-method :get}
        open-promise
        (promise)
        message-promise
        (promise)
        socket
        (connect test-client upgrade-request
                 {:on-open (fn [socket response]
                             (deliver open-promise response))
                  :on-text (fn [socket message]
                             (deliver message-promise message))})]
    (try
      (let [response ^Response (deref open-promise)]
        (is (= 101 (.code response)))
        (is (not (realized? message-promise))))
      (.send socket "This is a test.")
      (let [message (deref message-promise)]
        (is (= "This is a test." message)))
      (finally
        (.close socket 1000 "Terminating.")))))