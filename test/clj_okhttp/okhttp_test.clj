(ns clj-okhttp.okhttp-test
  (:require [clojure.test :refer :all])
  (:require [clj-okhttp.okhttp :refer [->url]]))

(deftest ->url-test
  (testing "path params are encoded"
    (let [url       ["https://google.com" "thing stuff" "search"]
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
