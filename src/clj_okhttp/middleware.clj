(ns clj-okhttp.middleware
  (:require [clj-okhttp.protocols :as protos]
            [clj-okhttp.utilities :as utils]
            [clojure.string :as strings]
            [clj-okhttp.muuntaja :as mun]
            [muuntaja.core :as m])
  (:import [clojure.lang IPersistentMap]
           [okhttp3 Request]
           [java.io InputStream]))


(set! *warn-on-reflection* true)

(defn wrap-to-and-from-data [handler]
  (fn to-and-from-data-handler
    ([request]
     (let [req-obj (protos/transform Request request)
           res-obj (handler req-obj)]
       (with-meta
         (protos/transform IPersistentMap res-obj)
         {:request req-obj :response res-obj})))
    ([request respond raise]
     (let [req-obj (protos/transform Request request)]
       (handler req-obj #(respond (with-meta
                                    (protos/transform IPersistentMap %1)
                                    {:request req-obj :response %})) raise)))))


(defn wrap-lowercase-response-headers [handler]
  (letfn [(transform [response]
            (update response :headers #(utils/map-keys strings/lower-case %)))]
    (fn lowercase-headers-handler
      ([request]
       (transform (handler request)))
      ([request respond raise]
       (handler request (comp respond transform) raise)))))

(def muuntaja-factory
  (memoize (fn [opts] (m/create opts))))

(defn wrap-init-muuntaja [handler]
  (letfn [(init-muuntaja [request]
            (muuntaja-factory (or (:muuntaja request) mun/defaults)))]
    (fn init-muuntaja-handler
      ([request]
       (handler (assoc request :muuntaja (init-muuntaja request))))
      ([request respond raise]
       (handler (assoc request :muuntaja (init-muuntaja request)) respond raise)))))

(defn wrap-decode-responses [handler]
  (fn decode-response-handler
    ([request]
     (let [response (handler request)]
       (if (not= (:as request) :stream)
         (with-open [_ ^InputStream (:body response)]
           (mun/format-response request response))
         response)))
    ([request respond raise]
     (handler request
              (fn [response]
                (try
                  (respond
                    (if (not= (:as request) :stream)
                      (with-open [_ ^InputStream (:body response)]
                        (mun/format-response request response))
                      response))
                  (catch Throwable e
                    (raise e))))
              raise))))

(defn wrap-encode-requests [handler]
  (fn encode-request-handler
    ([request]
     (handler (mun/format-request request)))
    ([request respond raise]
     (try
       (handler (mun/format-request request) respond raise)
       (catch Throwable e
         (raise e))))))


(defn wrap-basic-authentication [handler]
  (fn basic-auth-handler
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))

(defn wrap-parse-link-headers [handler]
  (fn parse-links-handler
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))

(defn wrap-throw-exceptional-responses [handler]
  (fn throw-exception-responses-handler
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))

