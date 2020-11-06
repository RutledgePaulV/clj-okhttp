(ns clj-okhttp.middleware
  (:require [clj-okhttp.utilities :as utils]
            [clojure.string :as strings]
            [clj-okhttp.formats :as mun]
            [clj-okhttp.links :as links]
            [clj-okhttp.okhttp :as okhttp]))


(set! *warn-on-reflection* true)

(defn request-transformer [handler f]
  (fn ([request] (handler (f request)))
    ([request respond raise]
     (try (handler (f request) respond raise)
          (catch Throwable e (raise e))))))

(defn response-transformer [handler f]
  (fn ([request] (f (handler request)))
    ([request respond raise]
     (try (handler request (comp respond f) raise)
          (catch Throwable e (raise e))))))

(defn wrap-lowercase-request-headers [handler]
  (letfn [(lowercase-request-headers [response]
            (update response :headers #(utils/map-keys (comp strings/lower-case name) %)))]
    (request-transformer handler lowercase-request-headers)))

(defn wrap-lowercase-response-headers [handler]
  (letfn [(lowercase-response-headers [response]
            (update response :headers #(utils/map-keys (comp strings/lower-case name) %)))]
    (response-transformer handler lowercase-response-headers)))

(defn wrap-basic-authentication [handler]
  (letfn [(basic-authentication-header [request]
            (if-some [[username password] (:basic-auth request)]
              (let [value (utils/basic-auth username password)]
                (-> request
                    (assoc-in [:headers "authorization"] value)
                    (dissoc :basic-auth)))
              request))]
    (request-transformer handler basic-authentication-header)))

(defn wrap-parse-link-headers [handler]
  (letfn [(parse-link-headers [response]
            (let [links
                  (if-some [header (get-in response [:headers "link"])]
                    (links/read-link-headers header)
                    {})]
              (assoc response :links links)))]
    (response-transformer handler parse-link-headers)))

(defn wrap-init-muuntaja [handler]
  (letfn [(init-muuntaja [request]
            (let [opts (or (:muuntaja request) mun/defaults)]
              (assoc request :muuntaja (mun/muuntaja-factory opts))))]
    (request-transformer handler init-muuntaja)))

(defn wrap-decode-responses [handler]
  (fn decode-response-handler
    ([request]
     (let [response (handler request)]
       (if (not= (:as request) :stream)
         (mun/format-response request response)
         response)))
    ([request respond raise]
     (handler request
              (fn [response]
                (try
                  (respond
                    (if (not= (:as request) :stream)
                      (mun/format-response request response)
                      response))
                  (catch Throwable e
                    (raise e))))
              raise))))

(defn wrap-okhttp-request-body [handler]
  (request-transformer handler mun/format-request))

(defn wrap-okhttp-request-url [handler]
  (letfn [(transformer [{:keys [url query-params] :as request}]
            (-> request
                (assoc :url (okhttp/->url url query-params))
                (dissoc :query-params)))]
    (request-transformer handler transformer)))

(defn wrap-okhttp-request-headers [handler]
  (letfn [(okhttp-request-headers [request]
            (update request :headers okhttp/->headers))]
    (request-transformer handler okhttp-request-headers)))

(defn wrap-okhttp-response-headers [handler]
  (letfn [(okhttp-response-headers [response]
            (update response :headers okhttp/<-headers))]
    (response-transformer handler okhttp-response-headers)))

(defn wrap-okhttp-response-body [handler]
  (letfn [(okhttp-response-body [response]
            (update response :body okhttp/<-response-body))]
    (response-transformer handler okhttp-response-body)))

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


