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

(defn- http-handler
  (^Map [^OkHttpClient client ^Request request]
   (.execute (.newCall client request)))
  (^Call [^OkHttpClient client ^Request request ^IFn respond ^IFn raise]
   (let [call     (.newCall client request)
         callback (reify Callback
                    (onFailure [this call exception]
                      (raise exception))
                    (onResponse [this call response]
                      (respond response)))]
     (.enqueue call callback)
     call)))

(defn- websocket-handler
  (^WebSocket [^OkHttpClient client on-open-promise on-failure-promise
               {:keys [on-bytes on-text on-closing on-closed]} ^Request request ^IFn respond ^IFn raise]
   (let [listener
         (proxy [WebSocketListener] []
           (onOpen [socket response]
             (deliver on-open-promise {:socket socket :response response})
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
             (deliver on-failure-promise {:socket socket :response response})
             (raise throwable)))]
     (.newWebSocket client request listener))))

(defn- compile-handler [handler middleware]
  (reduce #(%2 %1) handler (rseq (vec middleware))))

(defn create-client
  (^OkHttpClient []
   (create-client {}))
  (^OkHttpClient [{:keys [] :as opts}]
   (OkHttpClient.)))

(defn request*
  (^Map [^OkHttpClient client request]
   (let [handler    #(http-handler client %)
         handler+mw (if (not-empty (:middleware request))
                      (compile-handler handler (:middleware request))
                      (compile-handler handler default-middleware))]
     (handler+mw request)))
  (^Call [^OkHttpClient client request respond raise]
   (let [handler    #(http-handler client %)
         handler+mw (if (not-empty (:middleware request))
                      (compile-handler handler (:middleware request))
                      (compile-handler handler default-middleware))]
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
  ^WebSocket [^OkHttpClient client upgrade-request
              {:keys [on-open on-bytes on-text on-closing on-closed on-failure]
               :or   {on-open    (fn default-on-open-callback [socket response])
                      on-bytes   (fn default-on-bytes-callback [socket message])
                      on-text    (fn default-on-text-callback [socket message])
                      on-closing (fn default-on-closing-callback [socket code reason])
                      on-closed  (fn default-on-closed-callback [socket code reason])
                      on-failure (fn default-on-failure-callback [socket exception response])}}]
  (let [[open-prom failure-prom] [(promise) (promise)]
        handler     #(websocket-handler
                       client
                       open-prom failure-prom
                       {:on-bytes   on-bytes
                        :on-text    on-text
                        :on-closing on-closing
                        :on-closed  on-closed}
                       %1 %2 %3)
        handler+mw  (if (not-empty (:middleware upgrade-request))
                      (compile-handler handler (:middleware upgrade-request))
                      (compile-handler handler default-middleware))
        upgrade-req (-> upgrade-request
                        (assoc :as :stream)
                        (update :request-method #(or % :get))
                        (assoc-in [:headers "upgrade"] "websocket"))]
    (handler+mw
      upgrade-req
      (fn [response]
        (let [{:keys [socket]} (deref open-prom)]
          (on-open socket response)))
      (fn [exception]
        (let [{:keys [socket response]} (deref failure-prom)]
          (on-failure socket exception response))))))