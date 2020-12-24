(ns clj-okhttp.ssl-test
  (:require [clojure.test :refer :all])
  (:require [clj-okhttp.ssl :refer :all]
            [clojure.java.io :as io]))




(deftest ssl-test
  (let [server-certificate (slurp (io/resource "server/server_cert.pem"))
        client-certificate (slurp (io/resource "client/client_cert.pem"))
        client-key         (slurp (io/resource "client/private/client_key.pem"))
        trust-managers     (trust-managers [server-certificate])
        key-managers       (key-managers client-certificate client-key)]
    (is (pos? (alength trust-managers)))
    (is (pos? (alength key-managers)))))