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
  (letfn [(prepare-response [{:keys [muuntaja as]} {:keys [body] :as response}]
            (let [decoded (mun/format-stream muuntaja (get-in response [:headers "content-type"]) as body)]
              (assoc response :body decoded)))]
    (fn decode-response-handler
      ([request]
       (prepare-response request (handler request)))
      ([request respond raise]
       (handler request
                (fn [response]
                  (try
                    (respond (prepare-response request response))
                    (catch Throwable e (raise e))))
                raise)))))

(defn wrap-okhttp-request-body [handler]
  (request-transformer handler mun/format-request))

(defn wrap-origin-header-if-websocket [handler]
  (letfn [(transformer [{:keys [url query-params headers] :as req}]
            (if (and (= "websocket" (get-in headers ["upgrade"]))
                     (nil? (get-in req [:headers "origin"])))
              (->> (okhttp/->url url query-params)
                   (utils/url->origin)
                   (assoc-in req [:headers "origin"]))
              req))]
    (request-transformer handler transformer)))

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

(def default-middleware
  "The standard set of middleware used by this library to transform
   from a ring style request map -> OkHttp.Request -> OkHttp.Response
   -> ring style response map. Along the way this middleware takes care
   of request encoding and response decoding. If you choose to pass custom
   middleware into a request function you probably still want to include most
   if not all of these (order matters!)"
  [wrap-init-muuntaja
   wrap-lowercase-request-headers
   wrap-basic-authentication
   wrap-parse-link-headers
   wrap-decode-responses
   wrap-lowercase-response-headers
   wrap-okhttp-request-url
   wrap-origin-header-if-websocket
   wrap-okhttp-request-body
   wrap-okhttp-request-headers
   wrap-okhttp-response-body
   wrap-okhttp-response-headers
   wrap-okhttp-request-response])

(defn combine-middleware-chains [middleware1 middleware2]
  (let [m (meta middleware2)]
    (cond
      (true? (:replace m)) middleware2
      (true? (:append m)) (into middleware1 middleware2)
      (true? (:prepend m)) (into middleware2 middleware1)
      :otherwise
      (into middleware2 middleware1))))

(defonce per-client-middleware
  (utils/weakly-memoize-by-key
    (fn [_ middleware]
      (combine-middleware-chains default-middleware (or middleware [])))
    (fn [client _] client)))

(defn compile-middleware
  [client handler request]
  (let [global-mw (or (per-client-middleware client []) default-middleware)]
    (->> (rseq (if (not-empty (:middleware request))
                 (combine-middleware-chains global-mw (:middleware request))
                 global-mw))
         (reduce #(%2 %1) handler))))
