(ns clj-okhttp.utilities-test
  (:require [clojure.test :refer :all])
  (:require [clj-okhttp.utilities :refer [url->origin]])
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
