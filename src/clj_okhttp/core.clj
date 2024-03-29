(ns clj-okhttp.core
  (:require [clj-okhttp.middleware :as mw]
            [clj-okhttp.okhttp :as okhttp])
  (:refer-clojure :exclude [get])
  (:import [okhttp3 Request OkHttpClient Callback WebSocketListener WebSocket Call]
           [clojure.lang IFn]
           [java.util Map]))

(set! *warn-on-reflection* true)


(defn create-client
  "Creates a OkHttpClient instance with the specified options. Simple options can be
   expressed with clojure data but you may need to construct OkHttp object instances
   for the larger http client plugins. Note that you probably should not be using
   OkHttp interceptors and should instead provide your own ring-style middleware as
   the 'middleware' option. Middleware can be set per-client instance and per-request."
  (^OkHttpClient []
   (create-client {}))
  (^OkHttpClient [opts]
   (doto (okhttp/->http-client opts)
     (mw/per-client-middleware (:middleware opts []))))
  ; clone an existing client + optionally change options and middleware
  (^OkHttpClient [^OkHttpClient client opts]
   (doto (okhttp/->http-client client opts)
     (mw/per-client-middleware
       (with-meta
         (mw/combine-middleware-chains
           (mw/per-client-middleware client [])
           (:middleware opts []))
         {:replace true})))))

(defn request
  "Executes a http request. Requests consist of clojure data in the same style
   as other http client libraries like clj-http.

   The 2 arity invokes a synchronous request and returns a response map.

   The 4 arity invokes an asynchronous request and returns a OkHttp3.Call instance
   that can be used to cancel / abort the request as long as it's still in flight.
  "
  (^Map [^OkHttpClient client request]
   (let [handler    #(.execute (.newCall client ^Request %1))
         handler+mw (mw/compile-middleware client handler request)]
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
         handler+mw (mw/compile-middleware client handler request)]
     (handler+mw request respond raise))))

(defn get
  "Executes a http get request."
  (^Map [^OkHttpClient client url]
   (request client {:request-method :get :url url}))
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :get :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :get :url url) respond raise)))

(defn head
  "Executes a http head request."
  (^Map [^OkHttpClient client url]
   (request client {:request-method :head :url url}))
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :head :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :head :url url) respond raise)))

(defn options
  "Executes a http options request."
  (^Map [^OkHttpClient client url]
   (request client {:request-method :options :url url}))
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :options :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :options :url url) respond raise)))

(defn put
  "Executes a http put request."
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :put :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :put :url url) respond raise)))

(defn patch
  "Executes a http patch request."
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :patch :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :patch :url url) respond raise)))

(defn post
  "Executes a http post request."
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :post :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :post :url url) respond raise)))

(defn delete
  "Executes a http delete request."
  (^Map [^OkHttpClient client url]
   (request client {:request-method :delete :url url}))
  (^Map [^OkHttpClient client url req]
   (request client (assoc req :request-method :delete :url url)))
  (^Call [^OkHttpClient client url req respond raise]
   (request client (assoc req :request-method :delete :url url) respond raise)))

(defn connect
  "Opens a websocket connection using the provided http upgrade request.
   Registers callbacks for the various events in a websocket lifecycle.

   ring-style middleware are used to transform the upgrade request in the
   same way that they are used elsewhere for a http request. no middleware
   is applied per-message on the socket and messages are delivered to user
   callbacks in raw string/byte form.

   Returns an OkHttp3.Websocket instance that can be used to send messages
   on the open socket. Callbacks also receive this same instance so they can
   easily send replies to the other end of the socket.
   "
  (^WebSocket [^OkHttpClient client req
               {:keys [on-open on-bytes on-text on-closing on-closed on-failure]
                :or   {on-open    (fn default-on-open [socket response])
                       on-bytes   (fn default-on-bytes [socket message])
                       on-text    (fn default-on-text [socket message])
                       on-closing (fn default-on-closing [socket code reason])
                       on-closed  (fn default-on-closed [socket code reason])
                       on-failure (fn default-on-failure [socket exception response])}}]
   (let [handler     (fn upgrade-request-handler [req respond raise]
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
                                 (raise throwable)))]
                         (.newWebSocket client req listener)))
         handler+mw  (mw/compile-middleware client handler req)
         upgrade-req (-> req
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
         socket)))))


(defn success? [{:keys [status]}]
  (<= 200 status 299))

(defn redirect? [{:keys [status]}]
  (<= 300 status 399))

(defn client-error? [{:keys [status]}]
  (<= 400 status 499))

(defn server-error? [{:keys [status]}]
  (<= 500 status 599))

(defn bad-request? [{:keys [status]}]
  (== 400 status))

(defn unauthorized? [{:keys [status]}]
  (== 401 status))

(defn forbidden? [{:keys [status]}]
  (== 403 status))

(defn not-found? [{:keys [status]}]
  (== 404 status))

(defn conflict? [{:keys [status]}]
  (== 409 status))

(defmacro case-status [status & range+clause]
  `(condp (fn [bounds# status#]
            (cond
              (number? bounds#)
              (= bounds# status#)
              (and (vector? bounds#) (= 1 (count bounds#)))
              (= (first bounds#) status#)
              (vector? bounds#)
              (<= (first bounds#) status# (second bounds#))
              (set? bounds#)
              (contains? bounds# status#)
              (list? bounds#)
              (contains? (set bounds#) status#)))
          ~status
     ~@range+clause))

(defmacro caselet-response [binding & range+clause]
  `(let [response# ~(second binding) ~(first binding) response#]
     (case-status (:status response#) ~@range+clause)))