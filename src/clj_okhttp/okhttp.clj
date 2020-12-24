(ns clj-okhttp.okhttp
  (:require [clojure.string :as strings]
            [clojure.java.io :as io]
            [clj-okhttp.ssl :as ssl]
            [muuntaja.protocols :as mp])
  (:import [okhttp3 HttpUrl Headers$Builder Request Request$Builder Headers Response ResponseBody Dispatcher ConnectionPool OkHttpClient OkHttpClient$Builder Interceptor EventListener$Factory OkHttpClient$Companion EventListener Authenticator CookieJar Dns CertificatePinner Cache ConnectionSpec Protocol CertificatePinner$Pin CertificatePinner$Builder FormBody FormBody$Builder MultipartBody MultipartBody$Builder RequestBody MediaType]
           [java.time Instant Duration]
           [java.util Date]
           [java.io FilterInputStream InputStream File]
           [clojure.lang IPersistentMap MultiFn]
           [java.util.concurrent TimeUnit]
           [okhttp3.internal.io FileSystem]
           [javax.net.ssl HostnameVerifier]
           [okio BufferedSink]
           [muuntaja.protocols StreamableResponse]
           [okhttp3.internal.http HttpMethod]))


(defn ->url ^HttpUrl [url query-params]
  (let [[^HttpUrl http-url segments]
        (cond
          (string? url)
          [(HttpUrl/parse url) []]
          (vector? url)
          (let [[begin & parts] (flatten url)]
            [(HttpUrl/parse begin) parts])
          (instance? HttpUrl url)
          [url []])]
    (if (or (not-empty segments) (not-empty query-params))
      (let [builder (.newBuilder http-url)]
        (doseq [segment segments]
          (.addPathSegment builder (if (keyword? segment) (name segment) (str segment))))
        (doseq [[k v] query-params v' (if (coll? v) v [v])]
          (.addQueryParameter builder (name k) (if (keyword? v') (name v') (str v'))))
        (.build builder))
      http-url)))

(defn ->headers ^Headers [headers]
  (let [builder (Headers$Builder.)
        grouped (group-by (comp class val) headers)]
    (doseq [[k v] (get grouped Date)]
      (.add builder (name k) ^Date v))
    (doseq [[k v] (get grouped Instant)]
      (.add builder (name k) ^Instant v))
    (doseq [[k v] (get grouped String)]
      (.add builder (name k) ^String v))
    (.build builder)))

(defn <-headers ^IPersistentMap [^Headers headers]
  (letfn [(reduction [agg i]
            (assoc! agg (.name headers i) (.value headers i)))]
    (persistent! (reduce reduction (transient {}) (range (.size headers))))))

(defn ->request ^Request [{:keys [request-method body headers url] :as req}]
  (let [method (strings/upper-case (name request-method))]
    (.build
      (doto (Request$Builder.)
        (.method method
                 (if (HttpMethod/requiresRequestBody method)
                   (or body (RequestBody/create (byte-array 0) nil))
                   body))
        (.headers headers)
        (.url ^HttpUrl url)))))

(defn <-response ^IPersistentMap [^Response response]
  {:status    (.code response)
   :headers   (.headers response)
   :body      (.body response)
   :message   (.message response)
   :protocol  (str (.protocol response))
   :sent-time (.sentRequestAtMillis response)
   :recv-time (.receivedResponseAtMillis response)})

(defn <-response-body ^InputStream [^ResponseBody body]
  (let [stream (.byteStream body)]
    (proxy [FilterInputStream] [stream]
      (close [] (.close stream) (.close body)))))

(defn ->dispatcher ^Dispatcher [dispatcher]
  (cond
    (instance? Dispatcher dispatcher)
    dispatcher
    (map? dispatcher)
    (let [{:keys [max-requests max-requests-per-host executor-service]
           :or   {max-requests 64 max-requests-per-host 5}} dispatcher]
      (doto (Dispatcher. executor-service)
        (.setMaxRequests max-requests)
        (.setMaxRequestsPerHost max-requests-per-host)))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->connection-pool ^ConnectionPool [connection-pool]
  (cond
    (or (nil? connection-pool) (instance? ConnectionPool connection-pool))
    connection-pool
    (map? connection-pool)
    (let [{:keys [max-idle-connections keep-alive-duration time-unit]
           :or   {max-idle-connections 5 keep-alive-duration 5 time-unit TimeUnit/MINUTES}} connection-pool]
      (ConnectionPool. max-idle-connections keep-alive-duration time-unit))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->interceptor ^Interceptor [interceptor-or-fn]
  (cond
    (or (nil? interceptor-or-fn) (instance? Interceptor interceptor-or-fn))
    interceptor-or-fn
    (ifn? interceptor-or-fn)
    (reify Interceptor (intercept [this chain] (interceptor-or-fn chain)))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->event-listener-factory ^EventListener$Factory [factory]
  (cond
    (or (nil? factory) (instance? EventListener$Factory factory))
    factory
    (ifn? factory)
    (reify EventListener$Factory (create [this call] (factory call)))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->authenticator ^Authenticator [authenticator]
  (cond
    (or (nil? authenticator) (instance? Authenticator authenticator))
    authenticator
    (ifn? authenticator)
    (reify Authenticator (authenticate [this route response] (authenticator route response)))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->cache ^Cache [cache]
  (cond
    (or (nil? cache) (instance? Cache cache))
    cache
    (map? cache)
    (let [{:keys [directory max-size file-system]
           :or   {file-system FileSystem/SYSTEM}} cache]
      (Cache. directory max-size file-system))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->connection-spec ^ConnectionSpec [connection-spec]
  (cond
    (or (nil? connection-spec) (instance? ConnectionSpec connection-spec))
    connection-spec
    (map? connection-spec)
    (let [{:keys [is-tls
                  supports-tls-extensions
                  cipher-suites-as-string
                  tls-versions-as-string]} connection-spec]
      (ConnectionSpec. is-tls supports-tls-extensions cipher-suites-as-string tls-versions-as-string))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->protocol ^Protocol [protocol]
  (cond
    (or (nil? protocol) (instance? Protocol protocol))
    protocol
    (string? protocol)
    (Protocol/get protocol)
    (keyword? protocol)
    (Protocol/get (name protocol))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->hostname-verifier ^HostnameVerifier [verifier]
  (cond
    (or (nil? verifier) (instance? HostnameVerifier verifier))
    verifier
    (ifn? verifier)
    (reify HostnameVerifier (verify [this hostname session] (verifier hostname session)))
    :otherwise
    (throw (IllegalArgumentException.))))

(defn ->certificate-pinner ^CertificatePinner [certificate-pinner]
  (cond
    (or (nil? certificate-pinner) (instance? CertificatePinner certificate-pinner))
    certificate-pinner
    (map? certificate-pinner)
    (let [{:keys [pins]} certificate-pinner
          builder (CertificatePinner$Builder.)]
      (doseq [{:keys [pattern pin]} pins]
        (.add builder pattern pin))
      (.build builder))
    :otherwise
    (throw (IllegalArgumentException.))))


(declare ->request-body)


(defn ->multipart-body ^MultipartBody [parts]
  (let [builder (doto (MultipartBody$Builder.)
                  (.setType (MediaType/parse
                              (or (some-> parts meta :mime-type)
                                  "multipart/form-data"))))]
    (doseq [{:keys [name content part-name mime-type]} parts]
      (.addFormDataPart builder (or part-name name) name (->request-body mime-type content)))
    (.build builder)))

(defn ->form-body ^FormBody [body]
  (let [builder (FormBody$Builder.)]
    (doseq [[k v] body]
      (.add builder (name k) (if (keyword? v) (name v) (str v))))
    (.build builder)))

(defn ->request-body ^RequestBody [content-type content]
  (let [media (MediaType/parse (or content-type "application/octet-stream"))]
    (cond
      (nil? content) nil

      (instance? RequestBody content)
      content

      (and (= "application/x-www-form-urlencoded" content-type) (map? content))
      (->form-body content)

      (and (= "multipart/form-data" content-type) (vector? content))
      (->multipart-body content)

      (or (instance? StreamableResponse content)
          (fn? content)
          (instance? MultiFn content))
      (proxy [RequestBody] []
        (contentLength [] -1)
        (contentType [] media)
        (writeTo [^BufferedSink sink]
          (content (.outputStream sink)))
        (isOneShot [] true))

      (instance? InputStream content)
      (proxy [RequestBody] []
        (contentLength [] -1)
        (contentType [] media)
        (writeTo [^BufferedSink sink]
          (with-open [in content]
            (io/copy in (.outputStream sink))))
        (isOneShot [] true))

      (string? content)
      (RequestBody/create ^String content media)

      (bytes? content)
      (RequestBody/create ^bytes content media)

      (instance? File content)
      (RequestBody/create ^File content media)

      :otherwise
      nil)))

(defn- add-interceptors [^OkHttpClient$Builder builder interceptors]
  (doseq [interceptor interceptors]
    (.addInterceptor builder (->interceptor interceptor)))
  builder)

(defn- add-networkInterceptors [^OkHttpClient$Builder builder interceptors]
  (doseq [interceptor interceptors]
    (.addNetworkInterceptor builder (->interceptor interceptor)))
  builder)

(defn- add-client-server-certs [^OkHttpClient$Builder builder server-certificates client-certificate client-key]
  (let [trust-managers     (ssl/trust-managers server-certificates)
        key-managers       (ssl/key-managers client-certificate client-key)
        socket-factory     (ssl/ssl-socket-factory trust-managers key-managers)
        x509-trust-manager (first (filter clj-okhttp.ssl/x509-trust-manager? trust-managers))]
    (.sslSocketFactory builder socket-factory x509-trust-manager)))

(defn- add-server-certs [^OkHttpClient$Builder builder server-certificates]
  (let [trust-managers     (ssl/trust-managers server-certificates)
        socket-factory     (ssl/ssl-socket-factory trust-managers nil)
        x509-trust-manager (first (filter ssl/x509-trust-manager? trust-managers))]
    (.sslSocketFactory builder socket-factory x509-trust-manager)))

(defn- ^Duration ->duration [x]
  (if (instance? Duration x) x (Duration/ofMillis x)))

(defn ->http-client ^OkHttpClient
  ([options]
   (->http-client (OkHttpClient$Builder.) options))
  ([builder {:keys [dispatcher connection-pool interceptors network-interceptors event-listener-factory
                    retry-on-connection-failure authenticator follow-redirects follow-ssl-redirects cookie-jar
                    cache dns proxy proxy-selector proxy-authenticator socket-factory ssl-socket-factory
                    x509-trust-manager connection-specs protocols hostname-verifier certificate-pinner
                    call-timeout connect-timeout read-timeout write-timeout ping-interval
                    min-websocket-message-to-compress server-certificates client-certificate client-key]}]
   (cond-> (cond
             (instance? OkHttpClient$Builder builder)
             builder
             (instance? OkHttpClient builder)
             (.newBuilder ^OkHttpClient builder)
             :otherwise
             (throw (IllegalArgumentException. (format "Don't know how to create a OkHttpClient$Builder from %s" (str (class builder))))))
     (some? dispatcher) (.dispatcher (->dispatcher dispatcher))
     (some? connection-pool) (.connectionPool (->connection-pool connection-pool))
     (not-empty interceptors) (add-interceptors interceptors)
     (not-empty network-interceptors) (add-networkInterceptors network-interceptors)
     (some? event-listener-factory) (.eventListenerFactory (->event-listener-factory event-listener-factory))
     (some? retry-on-connection-failure) (.retryOnConnectionFailure retry-on-connection-failure)
     (some? authenticator) (.authenticator (->authenticator authenticator))
     (some? follow-redirects) (.followRedirects follow-redirects)
     (some? follow-ssl-redirects) (.followSslRedirects follow-ssl-redirects)
     (some? cookie-jar) (.cookieJar cookie-jar)
     (some? cache) (.cache (->cache cache))
     (some? dns) (.dns dns)
     (some? proxy) (.proxy proxy)
     (some? proxy-selector) (.proxySelector proxy-selector)
     (some? proxy-authenticator) (.proxyAuthenticator (->authenticator proxy-authenticator))
     (some? socket-factory) (.socketFactory socket-factory)
     (and (some? ssl-socket-factory) (nil? x509-trust-manager)) (.sslSocketFactory ssl-socket-factory)
     (and (some? ssl-socket-factory) (some? x509-trust-manager)) (.sslSocketFactory ssl-socket-factory x509-trust-manager)
     (and (nil? ssl-socket-factory) server-certificates client-certificate client-key) (add-client-server-certs server-certificates client-certificate client-key)
     (and (nil? ssl-socket-factory) server-certificates (nil? client-certificate) (nil? client-key)) (add-server-certs server-certificates)
     (not-empty connection-specs) (.connectionSpecs (mapv ->connection-spec connection-specs))
     (not-empty protocols) (.protocols (mapv ->protocol protocols))
     (some? hostname-verifier) (.hostnameVerifier (->hostname-verifier hostname-verifier))
     (some? certificate-pinner) (.certificatePinner (->certificate-pinner certificate-pinner))
     (some? call-timeout) (.callTimeout (->duration call-timeout))
     (some? connect-timeout) (.connectTimeout (->duration connect-timeout))
     (some? read-timeout) (.readTimeout (->duration read-timeout))
     (some? write-timeout) (.writeTimeout (->duration write-timeout))
     (some? ping-interval) (.pingInterval (->duration ping-interval))
     (some? min-websocket-message-to-compress) (.minWebSocketMessageToCompress min-websocket-message-to-compress)
     :always (.build))))
