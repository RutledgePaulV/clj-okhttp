(ns clj-okhttp.core-test
  (:require [clojure.test :refer :all]
            [clj-okhttp.core :refer :all]
            [muuntaja.core :as m]
            [clojure.string :as strings]
            [clj-okhttp.support :as sup]
            [clojure.string :as str])
  (:refer-clojure :exclude [get])
  (:import [okhttp3 Response Call]
           [okio ByteString]
           [java.io InputStream]))

(def test-client (create-client {:connect-timeout 2000}))


(deftest format-request-and-response-test

  (testing "json request encoded by malli as byte array input stream"
    (let [input-stream-body
          {:body     {:test "stuff"}
           :muuntaja (assoc m/default-options :return :input-stream)
           :headers  {"content-type" "application/json"}}
          response
          (patch test-client [(sup/get-base-url) "patch"] input-stream-body)]
      (is (= 200 (:status response)))
      (is (= {:test "stuff"} (get-in response [:body :json])))))

  (testing "edn request encoded by malli as output stream callback"
    (let [output-stream-body
          {:body     {:test "stuff"}
           :muuntaja (assoc m/default-options :return :output-stream)
           :headers  {"content-type" "application/edn"}}
          response
          (patch test-client [(sup/get-base-url) "patch"] output-stream-body)]
      (is (= 200 (:status response)))
      (is (= (pr-str {:test "stuff"}) (get-in response [:body :data])))))

  (testing "transit request encoded by malli as byte array"
    (let [bytes-body
          {:body     {:test "stuff"}
           :muuntaja (assoc m/default-options :return :bytes)
           :headers  {"content-type" "application/transit+json"}}
          response
          (patch test-client [(sup/get-base-url) "patch"] bytes-body)]
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
          call    (get test-client [(sup/get-base-url) "get"] {} respond raise)]
      (is (instance? Call call))
      (let [response (deref res)]
        (is (= 200 (:status response)))
        (is (not-empty (:body response)))))))

(deftest content-encodings
  (testing "gzip"
    (let [response (get test-client [(sup/get-base-url) "gzip"])]
      (is (:gzipped (:body response))))))

(deftest streaming-response
  (let [request  {:as :stream}
        response (get test-client [(sup/get-base-url) "get"] request)]
    (is (= 200 (:status response)))
    (is (instance? java.io.InputStream (:body response)))
    (is (not (strings/blank? (slurp (:body response)))))))

(deftest basic-auth
  (let [request  {:basic-auth ["user" "password"]}
        response (get test-client [(sup/get-base-url) "basic-auth" "user" "password"] request)]
    (is (= 200 (:status response)))
    (is (= {:authenticated true :user "user"} (:body response)))))

(deftest forms
  (let [request  {:form-params {:test "something"}
                  :headers     {"content-type" "application/x-www-form-urlencoded"}}
        response (post test-client [(sup/get-base-url) "post"] request)]
    (is (= 200 (:status response)))
    (is (= {:test "something"} (get-in response [:body :form])))))

(deftest cloning
  (let [cloned (create-client test-client {:read-timeout 1})]
    (is (= 1 (.readTimeoutMillis cloned)))
    (is (= 2000 (.connectTimeoutMillis cloned)))))

(deftest large
  (let [request  {:as :stream}
        length   102400
        response (get test-client [(sup/get-base-url) "bytes" length] request)]
    (is (= length (with-open [body (:body response)]
                    (loop [sum 0]
                      (let [bite (.read ^InputStream body)]
                        (if (neg? bite)
                          sum
                          (recur (inc sum))))))))))

(deftest response-handling
  (is (map? (caselet-response
              [{:keys [body]} (get test-client [(sup/get-base-url) "get"])]
              200 body
              false))))

(deftest websocket-test
  (let [upgrade-request {:url            (sup/get-base-wss-url)
                         :request-method :get}
        open-promise    (promise)
        message-promise (promise)
        bites-promise   (promise)
        socket          (connect test-client
                                 upgrade-request
                                 {:on-open  (fn [_socket response]
                                              (deliver open-promise response))
                                  :on-text  (fn [_socket message]
                                              (when-not (str/includes? message "Request served by")
                                                (deliver message-promise message)))
                                  :on-bytes (fn [_socket bites]
                                              (deliver bites-promise bites))})]
    (try
      (let [response ^Response (deref open-promise)]
        (is (= 101 (.code response)))
        (is (not (realized? message-promise)))
        (is (not (realized? bites-promise))))
      (.send socket "This is a test.")
      (.send socket (ByteString/of (.getBytes "This is a test2.")))
      (let [message (deref message-promise)]
        (is (= "This is a test." message)))
      (let [message ^ByteString (deref bites-promise)]
        (is (= "This is a test2." (.utf8 message))))
      (finally
        (.close socket 1000 "Terminating.")))))

(deftest response-checker-tests
  (testing "success"
    (is (success? {:status 200}))
    (is (success? {:status 201}))
    (is (not (success? {:status 401}))))

  (testing "redirect"
    (is (redirect? {:status 307}))
    (is (not (redirect? {:status 401}))))

  (testing "not found"
    (is (not-found? {:status 404}))
    (is (not (not-found? {:status 401}))))

  (testing "conflict"
    (is (conflict? {:status 409}))
    (is (not (conflict? {:status 401}))))

  (testing "bad request"
    (is (bad-request? {:status 400}))
    (is (not (bad-request? {:status 401}))))

  (testing "unauthorized"
    (is (unauthorized? {:status 401}))
    (is (not (unauthorized? {:status 400}))))

  (testing "forbidden"
    (is (forbidden? {:status 403}))
    (is (not (forbidden? {:status 401}))))

  (testing "client error"
    (is (client-error? {:status 403}))
    (is (client-error? {:status 401}))
    (is (not (client-error? {:status 500}))))

  (testing "server error"
    (is (server-error? {:status 500}))
    (is (server-error? {:status 503}))
    (is (not (server-error? {:status 499})))))
