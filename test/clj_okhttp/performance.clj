(ns clj-okhttp.performance
  (:require [clojure.test :refer :all]
            [clj-okhttp.core :as okhttp]
            [clj-http.client :as apachehttp]
            [clojure.java.io :as io]
            [criterium.core :as crit]
            [clj-okhttp.support :as sup])
  (:import [java.io ByteArrayOutputStream]))

(def apache-http-pool
  (clj-http.conn-mgr/make-reusable-conn-manager {}))

(alter-var-root #'clj-http.conn-mgr/*connection-manager*
  (constantly apache-http-pool))

(def ok-http-client
  (okhttp/create-client))

(defn clj-http-small-file []
  (let [response (apachehttp/get (str (sup/get-base-url) "/image/jpeg") {:as :stream})
        bites    (with-open [in (:body response) out (ByteArrayOutputStream.)]
                   (io/copy in out)
                   (.toByteArray out))]
    (alength bites)))

(defn clj-okhttp-small-file []
  (let [response (okhttp/get ok-http-client (str (sup/get-base-url) "/image/jpeg") {:as :stream})
        bites    (with-open [in (:body response) out (ByteArrayOutputStream.)]
                   (io/copy in out)
                   (.toByteArray out))]
    (alength bites)))

(defn clj-http-json []
  (let [response (apachehttp/get (str (sup/get-base-url) "/get") {:as :json-strict})]
    (map? (:body response))))

(defn clj-okhttp-json []
  (let [response (okhttp/get ok-http-client (str (sup/get-base-url) "/get"))]
    (map? (:body response))))


(deftest ^:performance small-file-performance
  (let [[clj-http clj-okhttp]
        (crit/with-progress-reporting
          (crit/benchmark-round-robin
            [(clj-http-small-file) (clj-okhttp-small-file)]
            {:samples 10}))]
    (clojure.pprint/pprint
      {:clj-http   (select-keys clj-http [:mean :lower-q :upper-q])
       :clj-okhttp (select-keys clj-okhttp [:mean :lower-q :upper-q])})
    (is (< (-> clj-okhttp :mean first) (-> clj-http :mean first)))
    (is (< (-> clj-okhttp :lower-q first) (-> clj-http :lower-q first)))
    (is (< (-> clj-okhttp :upper-q first) (-> clj-http :upper-q first)))))

(deftest ^:performance json-performance
  (let [[clj-http clj-okhttp]
        (crit/with-progress-reporting
          (crit/benchmark-round-robin
            [(clj-http-json) (clj-okhttp-json)]
            {:samples 20}))]
    (clojure.pprint/pprint
      {:clj-http   (select-keys clj-http [:mean :lower-q :upper-q])
       :clj-okhttp (select-keys clj-okhttp [:mean :lower-q :upper-q])})
    (is (< (-> clj-okhttp :mean first) (-> clj-http :mean first)))
    (is (< (-> clj-okhttp :lower-q first) (-> clj-http :lower-q first)))
    (is (< (-> clj-okhttp :upper-q first) (-> clj-http :upper-q first)))))
