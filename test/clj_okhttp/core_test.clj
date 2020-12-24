(ns clj-okhttp.core-test
  (:require [clojure.test :refer :all]
            [clj-okhttp.core :refer :all]
            [muuntaja.core :as m]
            [clojure.string :as strings])
  (:refer-clojure :exclude [get])
  (:import [okhttp3 Response Call]))

(def test-client (create-client {:connect-timeout 2000}))


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

(deftest link-parsing
  (let [request  {:query-params {:Link "<https://example.com>; rel=\"preconnect\""}}
        response (get test-client "https://httpbin.org/response-headers" request)]
    (is (= {:preconnect {:href "https://example.com"}} (:links response)))))

(deftest asynchronous
  (testing "get"
    (let [res     (promise)
          respond (partial deliver res)
          raise   (partial deliver res)
          call    (get test-client "https://httpbin.org/get" {} respond raise)]
      (is (instance? Call call))
      (let [response (deref res)]
        (is (= 200 (:status response)))
        (is (not-empty (:body response)))))))

(deftest content-encodings
  (testing "gzip"
    (let [response (get test-client "https://httpbin.org/gzip")]
      (is (:gzipped (:body response))))))

(deftest streaming-response
  (let [request  {:as :stream}
        response (get test-client "https://httpbin.org/get" request)]
    (is (= 200 (:status response)))
    (is (instance? java.io.InputStream (:body response)))
    (is (not (strings/blank? (slurp (:body response)))))))

(deftest basic-auth
  (let [request  {:basic-auth ["user" "password"]}
        response (get test-client "https://httpbin.org/basic-auth/user/password" request)]
    (is (= 200 (:status response)))
    (is (= {:authenticated true :user "user"} (:body response)))))


(deftest cloning
  (let [cloned (create-client test-client {:read-timeout 1})]
    (is (= 1 (.readTimeoutMillis cloned)))
    (is (= 2000 (.connectTimeoutMillis cloned)))))

(deftest response-handling
  (is (map? (caselet-response
              [{:keys [body]} (get test-client "https://httpbin.org/get")]
              200 body
              false))))

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