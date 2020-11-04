(ns clj-okhttp.middleware
  (:require [clj-okhttp.utilities :as utils]
            [clojure.string :as strings]
            [clj-okhttp.muuntaja :as mun]
            [clj-okhttp.links :as links]
            [clj-okhttp.okhttp :as okhttp])
  (:import [java.io InputStream]))


(set! *warn-on-reflection* true)

(defn wrap-lowercase-response-headers [handler]
  (letfn [(transform [response]
            (update response :headers #(utils/map-keys strings/lower-case %)))]
    (fn lowercase-headers-handler
      ([request]
       (transform (handler request)))
      ([request respond raise]
       (handler request (comp respond transform) raise)))))


(defn wrap-init-muuntaja [handler]
  (letfn [(init-muuntaja [request]
            (mun/muuntaja-factory (or (:muuntaja request) mun/defaults)))]
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

(defn wrap-okhttp-request-body [handler]
  (fn encode-request-handler
    ([request]
     (handler (mun/format-request request)))
    ([request respond raise]
     (try
       (handler (mun/format-request request) respond raise)
       (catch Throwable e
         (raise e))))))

(defn wrap-okhttp-request-url [handler]
  (fn okhttp-request-url-handler
    ([{:keys [query-params url] :as request}]
     (handler (assoc request :url (okhttp/->url url query-params))))
    ([{:keys [query-params url] :as request} respond raise]
     (try (handler (assoc request :url (okhttp/->url url query-params)) respond raise)
          (catch Throwable e (raise e))))))

(defn wrap-okhttp-request-headers [handler]
  (fn okhttp-request-headers-handler
    ([{:keys [headers] :as request}]
     (handler (assoc request :headers (okhttp/->headers headers))))
    ([{:keys [headers] :as request} respond raise]
     (try (handler (assoc request :headers (okhttp/->headers headers)) respond raise)
          (catch Throwable e (raise e))))))

(defn wrap-okhttp-response-headers [handler]
  (fn okhttp-response-headers-handler
    ([request]
     (let [response (handler request)]
       (update response :headers okhttp/<-headers)))
    ([request respond raise]
     (try (handler request
                   (fn [response]
                     (respond (update response :headers okhttp/<-headers)))
                   raise)
          (catch Throwable e (raise e))))))

(defn wrap-okhttp-response-body [handler]
  (fn okhttp-response-body-handler
    ([request]
     (update (handler request) :body okhttp/<-response-body))
    ([request respond raise]
     (try (handler request
                   (fn [response]
                     (respond (update response :body okhttp/<-response-body)))
                   raise)
          (catch Throwable e (raise e))))))

(defn wrap-okhttp-request-response [handler]
  (fn okhttp-request-handler
    ([request]
     (let [final-request (okhttp/->request request)
           response      (handler final-request)]
       (with-meta (okhttp/<-response response)
         {:request final-request :response response})))
    ([request respond raise]
     (try
       (let [final-request (okhttp/->request request)]
         (handler
           final-request
           (fn [response]
             (respond
               (with-meta
                 (okhttp/<-response response)
                 {:request final-request :response response})))
           raise))
       (catch Throwable e (raise e))))))

(defn wrap-basic-authentication [handler]
  (fn basic-auth-handler
    ([request]
     (if-some [[username password] (:basic-auth request)]
       (let [value (utils/basic-auth username password)]
         (-> request
             (assoc-in [:headers "Authorization"] value)
             (dissoc :basic-auth)
             (handler)))
       (handler request)))
    ([request respond raise]
     (if-some [[username password] (:basic-auth request)]
       (try
         (let [value (utils/basic-auth username password)]
           (-> request
               (assoc-in [:headers "Authorization"] value)
               (dissoc :basic-auth)
               (handler respond raise)))
         (catch Throwable e (raise e)))
       (handler request respond raise)))))

(defn wrap-parse-link-headers [handler]
  (letfn [(extract-links [response]
            (if-some [header (get-in response [:headers "link"])]
              (links/read-link-headers header)
              []))]
    (fn parse-links-handler
      ([request]
       (let [response (handler request)]
         (assoc response :links (extract-links response))))
      ([request respond raise]
       (letfn [(responder [response]
                 (respond (assoc response :links (extract-links response))))]
         (handler request responder raise))))))

