(ns clj-okhttp.core
  (:require [clj-okhttp.middleware :as mw])
  (:import [okhttp3 Request OkHttpClient Callback WebSocketListener WebSocket Call]
           [clojure.lang IFn]
           [java.util Map]))

(def default-middleware
  [mw/wrap-init-muuntaja
   mw/wrap-basic-authentication
   mw/wrap-parse-link-headers
   mw/wrap-decode-responses
   mw/wrap-lowercase-response-headers
   mw/wrap-okhttp-request-url
   mw/wrap-okhttp-request-body
   mw/wrap-okhttp-response-body
   mw/wrap-okhttp-request-headers
   mw/wrap-okhttp-response-headers
   mw/wrap-okhttp-request-response])

(defn create-client
  (^OkHttpClient []
   (create-client {}))
  (^OkHttpClient [{:keys [] :as opts}]
   (OkHttpClient.)))

(defn request*
  (^Map [^OkHttpClient client request]
   (let [handler    #(.execute (.newCall client ^Request %1))
         handler+mw (->> (rseq (or (not-empty (:middleware request)) default-middleware))
                         (reduce #(%2 %1) handler))]
     (handler+mw request)))
  (^Call [^OkHttpClient client request respond raise]
   (let [handler    #(let [call     (.newCall client ^Request %1)
                           callback (reify Callback
                                      (onFailure [this call exception]
                                        (^IFn %3 exception))
                                      (onResponse [this call response]
                                        (^IFn %2 response)))]
                       (.enqueue call callback)
                       call)
         handler+mw (->> (rseq (or (not-empty (:middleware request)) default-middleware))
                         (reduce #(%2 %1) handler))]
     (handler+mw request respond raise))))

(defn get*
  (^Map [^OkHttpClient client url]
   (request* client {:request-method :get :url url}))
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :get :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :get :url url) respond raise)))

(defn head
  (^Map [^OkHttpClient client url]
   (request* client {:request-method :head :url url}))
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :head :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :head :url url) respond raise)))

(defn options
  (^Map [^OkHttpClient client url]
   (request* client {:request-method :options :url url}))
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :options :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :options :url url) respond raise)))

(defn put
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :put :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :put :url url) respond raise)))

(defn patch
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :patch :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :patch :url url) respond raise)))

(defn post
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :post :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :post :url url) respond raise)))

(defn delete
  (^Map [^OkHttpClient client url]
   (request* client {:request-method :delete :url url}))
  (^Map [^OkHttpClient client url request]
   (request* client (assoc request :request-method :delete :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :delete :url url) respond raise)))

(defn connect
  ^WebSocket [^OkHttpClient client request
              {:keys [on-open on-bytes on-text on-closing on-closed on-failure]
               :or   {on-open    (fn default-on-open [socket response])
                      on-bytes   (fn default-on-bytes [socket message])
                      on-text    (fn default-on-text [socket message])
                      on-closing (fn default-on-closing [socket code reason])
                      on-closed  (fn default-on-closed [socket code reason])
                      on-failure (fn default-on-failure [socket exception response])}}]
  (let [handler     (fn upgrade-request-handler [request respond raise]
                      (let [listener
                            (proxy [WebSocketListener] []
                              (onOpen [socket response]
                                (on-open socket response)
                                (respond response))
                              (onMessage [socket message]
                                (if (string? message)
                                  (on-text socket message)
                                  (on-bytes socket message)))
                              (onClosing [socket code reason]
                                (on-closing socket code reason))
                              (onClosed [socket code reason]
                                (on-closed socket code reason))
                              (onFailure [socket throwable response]
                                (on-failure socket throwable response)
                                (when (some? response)
                                  (raise throwable))))]
                        (.newWebSocket client request listener)))
        handler+mw  (->> (rseq (or (not-empty (:middleware request))
                                   default-middleware))
                         (reduce #(%2 %1) handler))
        upgrade-req (-> request
                        (assoc :as :stream)
                        (update :request-method #(or % :get))
                        (assoc-in [:headers "upgrade"] "websocket"))
        outcome     (promise)
        socket      (handler+mw
                      upgrade-req
                      (fn [response]
                        (deliver outcome response))
                      (fn [exception]
                        (deliver outcome exception)))]
    (let [result (deref outcome)]
      (if (instance? Exception result)
        (throw result)
        socket))))