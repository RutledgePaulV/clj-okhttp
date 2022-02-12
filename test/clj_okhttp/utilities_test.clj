(ns clj-okhttp.utilities-test
  (:require [clojure.test :refer :all]
            [clj-okhttp.utilities :refer :all])
  (:import [okhttp3 HttpUrl$Builder]))

(deftest url->origin-test
  (is (= "https://google.com:3000"
         (url->origin
           (.build
             (doto (HttpUrl$Builder.)
               (.host "google.com")
               (.port 3000)
               (.scheme "https")
               (.addPathSegment "Test")))))))

(deftest flatten-query-params-test
  (testing "flattening of vectors"
    (is (= {"one" ["1"]} (flatten-query-params {"one" "1"})))
    (is (= {"one" ["1"]} (flatten-query-params {"one" ["1"]}))))

  (testing "fattening of maps"
    (is (= {"one" ["1"]} (flatten-query-params {"one" "1"})))
    (is (= {"one[two]" ["2"]} (flatten-query-params {"one" {"two" "2"}})))
    (is (= {"one[two]" ["2"] "one[three]" ["3"]} (flatten-query-params {"one" {"two" "2" "three" "3"}}))))

  (testing "flattening of mixed"
    (is (= {"one[two]" ["3" "4"] "one[two][five]" ["6"]}
           (flatten-query-params {"one" {"two" ["3" "4" {"five" "6"}]}}))))

  (testing "keywords"
    (is (= {"one[two]" ["3" "4"] "one[two][five]" ["6"]}
           (flatten-query-params {:one {:two ["3" "4" {:five "6"}]}}))))

  (testing "non-string-values"
    (is (= {"one[two]" ["3" "4"] "one[two][five]" ["6"]}
           (flatten-query-params {:one {:two [3 4 {:five 6}]}})))))
