(ns clj-okhttp.okhttp-test
  (:require [clojure.test :refer :all])
  (:require [clj-okhttp.okhttp :refer :all]
            [clojure.java.io :as io])
  (:import [javax.net.ssl HostnameVerifier]
           [okhttp3 CertificatePinner Protocol ConnectionSpec Cache Authenticator EventListener EventListener$Factory Interceptor ConnectionPool Dispatcher FormBody MultipartBody RequestBody OkHttpClient Headers]
           [java.util.concurrent Executors]
           [java.io OutputStream ByteArrayInputStream]
           [java.util Date]
           [java.time Instant]))

(deftest ->url-test
  (testing "path params are encoded"
    (let [url       ["https://google.com" "thing stuff" :search]
          qp        {}
          http-url  (->url url qp)
          as-string (str http-url)]
      (is (= "https://google.com/thing%20stuff/search" as-string))))

  (testing "query params are encoded"
    (let [url       ["https://google.com" "search"]
          qp        {:q "thing stuff"}
          http-url  (->url url qp)
          as-string (str http-url)]
      (is (= "https://google.com/search?q=thing%20stuff" as-string))))

  (testing "multi valued query params are encoded"
    (let [url       ["https://google.com" "search"]
          qp        {:q ["thing stuff" :more]}
          http-url  (->url url qp)
          as-string (str http-url)]
      (is (= "https://google.com/search?q=thing%20stuff&q=more" as-string))))

  (testing "relative paths are allowed"
    (let [url       "https://google.com/search/../stuff"
          qp        {}
          http-url  (->url url qp)
          as-string (str http-url)]
      (is (= "https://google.com/stuff" as-string))))

  (testing "relative paths cannot be used to alter the domain name"
    (let [url       "https://google.com/search/..//stuff"
          qp        {}
          http-url  (->url url qp)
          as-string (str http-url)]
      (is (= "https://google.com//stuff" as-string)))
    (let [url       "https://google.com/search/../../stuff"
          qp        {}
          http-url  (->url url qp)
          as-string (str http-url)]
      (is (= "https://google.com/stuff" as-string)))))

(deftest ->hostname-verifier-test
  (let [verifier (->hostname-verifier (fn [hostname session] true))]
    (is (instance? HostnameVerifier verifier))
    (is (identical? verifier (->hostname-verifier verifier)))))

(deftest ->certificate-pinner-test
  (let [pinner (->certificate-pinner {:pins []})]
    (is (instance? CertificatePinner pinner))
    (is (identical? pinner (->certificate-pinner pinner)))))

(deftest ->protocol-test
  (let [proto (->protocol "http/1.0")]
    (is (instance? Protocol proto))
    (is (identical? proto (->protocol proto))))
  (is (instance? Protocol (->protocol "http/1.0")))
  (is (instance? Protocol (->protocol (keyword "http" "1.0"))))
  (is (instance? Protocol (->protocol :h2))))


(deftest ->connection-spec-test
  (let [spec (->connection-spec
               {:is-tls                  true
                :supports-tls-extensions false
                :cipher-suites-as-string []
                :tls-versions-as-string  []})]
    (is (instance? ConnectionSpec spec))
    (is (identical? spec (->connection-spec spec)))))

(deftest ->headers-test
  (let [headers (->headers {:LastModified  (Date.)
                            :ExpiresAt     (Instant/now)
                            :Demonstration "Test"
                            :Other         1000})]
    (is (instance? Headers headers))
    (is (= 4 (.size headers)))))

(deftest ->cache-test
  (let [cache (->cache {:directory "/" :max-size 10})]
    (is (instance? Cache cache))
    (is (identical? cache (->cache cache)))))

(deftest ->authenticator-test
  (let [authenticator (->authenticator (fn [route response] nil))]
    (is (instance? Authenticator authenticator))
    (is (identical? authenticator (->authenticator authenticator)))))

(deftest ->event-listener-factory-test
  (let [factory (->event-listener-factory
                  (fn [call] (proxy [EventListener] [])))]
    (is (instance? EventListener$Factory factory))
    (is (identical? factory (->event-listener-factory factory)))))

(deftest ->interceptor-test
  (let [interceptor (->interceptor (fn [chain]))]
    (is (instance? Interceptor interceptor))
    (is (identical? interceptor (->interceptor interceptor)))))

(deftest ->connection-pool-test
  (let [pool (->connection-pool {})]
    (is (instance? ConnectionPool pool))
    (is (identical? pool (->connection-pool pool)))))

(deftest ->dispatcher-test
  (let [dispatcher (->dispatcher
                     {:executor-service
                      (Executors/newFixedThreadPool 4)})]
    (is (instance? Dispatcher dispatcher))
    (is (identical? dispatcher (->dispatcher dispatcher)))))

(deftest ->request-body-test
  (let [body (->request-body "application/x-www-form-urlencoded" {:name "Test"})]
    (is (instance? FormBody body)))
  (let [body (->request-body "multipart/form-data" [{:name "Test" :content "stuff"}])]
    (is (instance? MultipartBody body)))
  (let [body (->request-body "application/json"
                             (fn [^OutputStream output-stream]
                               (.write output-stream (.getBytes "Test"))))]
    (is (instance? RequestBody body))
    (is (identical? body (->request-body "stuff" body))))
  (let [body (->request-body "application/json" (.getBytes "Stuff"))]
    (is (instance? RequestBody body)))
  (let [body (->request-body "application/json" "stuff")]
    (is (instance? RequestBody body)))
  (let [body (->request-body "application/json" (ByteArrayInputStream. (.getBytes "Stuff")))]
    (is (instance? RequestBody body)))
  (let [body (->request-body "application/json" (io/file (io/resource "server/server_cert.pem")))]
    (is (instance? RequestBody body))))

(deftest ->http-client-test

  (testing "mutual tls"
    (let [server-certificate (slurp (io/resource "server/server_cert.pem"))
          client-certificate (slurp (io/resource "client/client_cert.pem"))
          client-key         (slurp (io/resource "client/private/client_key.pem"))
          client             (->http-client
                               {:server-certificates [server-certificate]
                                :client-certificate  client-certificate
                                :client-key          client-key})]
      (is (instance? OkHttpClient client))))

  (testing "server certificate only"
    (let [server-certificate (slurp (io/resource "server/server_cert.pem"))
          client             (->http-client {:server-certificates [server-certificate]})]
      (is (instance? OkHttpClient client))))

  (testing "interceptors"
    (let [client (->http-client {:interceptors [(fn [chain])]})]
      (is (instance? OkHttpClient client))))

  (testing "network interceptors"
    (let [client (->http-client {:network-interceptors [(fn [chain])]})]
      (is (instance? OkHttpClient client)))))