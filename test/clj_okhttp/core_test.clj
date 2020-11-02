(ns clj-okhttp.core-test
  (:require [clojure.test :refer :all]
            [clj-okhttp.core :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))


(comment
  (def socket
    (websocket* (create-client)
                {:url            "https://echo.websocket.org"
                 :request-method :get
                 :headers        {"Upgrade" "websocket"}}
                {:on-open    (fn [socket response]
                               (println "on-open")
                               (clojure.pprint/pprint response))
                 :on-bytes   (fn [socket message]
                               (println "on-bytes")
                               (clojure.pprint/pprint message))
                 :on-text    (fn [socket message]
                               (println "on-text")
                               (clojure.pprint/pprint message))
                 :on-closing (fn [socket code reason]
                               (println "on-closing")
                               (clojure.pprint/pprint
                                 {:code code :reason reason}))
                 :on-closed  (fn [socket code reason]
                               (println "on-closed")
                               (clojure.pprint/pprint
                                 {:code code :reason reason}))
                 :on-failure (fn [socket exception response]
                               (println "on-failure")
                               (clojure.pprint/pprint
                                 {:exception exception :response response}))})))