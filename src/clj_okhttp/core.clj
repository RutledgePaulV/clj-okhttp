(ns clj-okhttp.core
  (:require [clj-okhttp.middleware :as mw])
  (:import [okhttp3 Request OkHttpClient Callback WebSocketListener WebSocket Call]
           [clojure.lang IFn])
  (:refer-clojure :exclude [get]))

(def default-middleware
  [mw/wrap-throw-exceptional-responses
   mw/wrap-lowercase-response-headers
   mw/wrap-parse-link-headers
   mw/wrap-encode-requests
   mw/wrap-decode-responses
   mw/wrap-basic-authentication
   mw/wrap-to-and-from-data])

(defn- http-handler
  ([^OkHttpClient client ^Request request]
   (.execute (.newCall client request)))
  ([^OkHttpClient client ^Request request ^IFn respond ^IFn raise]
   (let [call     (.newCall client request)
         callback (reify Callback
                    (onFailure [this call exception]
                      (raise exception))
                    (onResponse [this call response]
                      (respond response)))]
     (.enqueue call callback)
     call)))

(defn- websocket-handler
  ([^OkHttpClient client on-open-promise on-failure-promise
    {:keys [on-binary on-text on-closing on-closed]} ^Request request ^IFn respond ^IFn raise]
   (let [listener
         (proxy [WebSocketListener] []
           (onOpen [socket response]
             (deliver on-open-promise {:socket socket :response response})
             (respond response))
           (onMessage [socket message]
             (if (string? message)
               (on-text socket message)
               (on-binary socket message)))
           (onClosing [socket code reason]
             (on-closing socket code reason))
           (onClosed [socket code reason]
             (on-closed socket code reason))
           (onFailure [socket throwable response]
             (deliver on-failure-promise {:socket socket :response response})
             (raise throwable)))]
     (.newWebSocket client request listener))))

(defn- compile-handler [handler middleware]
  (reduce #(%2 %1) handler (rseq middleware)))

(defn ^OkHttpClient create-client
  ([] (create-client {}))
  ([{:keys [] :as opts}]
   (OkHttpClient.)))

(defn request*
  ([^OkHttpClient client request]
   (let [handler    (partial http-handler client)
         handler+mw (if (not-empty (:middleware request))
                      (compile-handler handler (:middleware request))
                      (compile-handler handler default-middleware))]
     (handler+mw request)))
  ([^OkHttpClient client request respond raise]
   (let [handler    (partial http-handler client)
         handler+mw (if (not-empty (:middleware request))
                      (compile-handler handler (:middleware request))
                      (compile-handler handler default-middleware))]
     (handler+mw request respond raise))))


(defn get
  ([^OkHttpClient client url]
   (request* client {:request-method :get :url url}))
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :get :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :get :url url) respond raise)))

(defn head
  ([^OkHttpClient client url]
   (request* client {:request-method :head :url url}))
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :head :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :head :url url) respond raise)))

(defn options
  ([^OkHttpClient client url]
   (request* client {:request-method :options :url url}))
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :options :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :options :url url) respond raise)))

(defn put
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :put :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :put :url url) respond raise)))

(defn patch
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :patch :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :patch :url url) respond raise)))

(defn post
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :post :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :post :url url) respond raise)))

(defn delete
  ([^OkHttpClient client url]
   (request* client {:request-method :delete :url url}))
  ([^OkHttpClient client url request]
   (request* client (assoc request :request-method :delete :url url)))
  (^Call [^OkHttpClient client url request respond raise]
   (request* client (assoc request :request-method :delete :url url) respond raise)))

(defn connect
  "Initiates a websocket connection against the destination of the upgrade-request and registers
   callbacks for the various events in a websocket lifecycle. Uses middleware to prepare the
   upgrade request and response prior to delivering to your callback."
  ^WebSocket [^OkHttpClient client upgrade-request {:keys [on-open on-bytes on-text on-closing on-closed on-failure]}]
  (let [[open-prom failure-prom] [(promise) (promise)]
        handler     (partial websocket-handler client open-prom failure-prom
                             {:on-binary  on-bytes
                              :on-text    on-text
                              :on-closing on-closing
                              :on-closed  on-closed})
        handler+mw  (if (not-empty (:middleware upgrade-request))
                      (compile-handler handler (:middleware upgrade-request))
                      (compile-handler handler default-middleware))
        upgrade-req (-> upgrade-request
                        (assoc-in [:headers "Upgrade"] "websocket"))]
    (handler+mw
      upgrade-req
      (fn [response]
        (let [{:keys [socket]} (deref open-prom)]
          (on-open socket response)))
      (fn [exception]
        (let [{:keys [socket response]} (deref failure-prom)]
          (on-failure socket exception response))))))