[![travisci](https://travis-ci.com/rutledgepaulv/clj-okhttp.svg?branch=master)](https://travis-ci.com/rutledgepaulv/clj-okhttp)
[![clojars](https://img.shields.io/clojars/v/clj-okhttp/clj-okhttp.svg)](https://clojars.org/clj-okhttp)
[![codecov](https://codecov.io/gh/rutledgepaulv/clj-okhttp/branch/master/graph/badge.svg)](https://codecov.io/gh/rutledgepaulv/clj-okhttp)
[![cljdoc](https://cljdoc.org/badge/clj-okhttp/clj-okhttp)](https://cljdoc.org/d/clj-okhttp/clj-okhttp/0.1.0-SNAPSHOT)

### Intro

clj-okhttp is a http and websocket client library built on [OkHttp](https://github.com/square/okhttp),
[Muuntaja](https://github.com/metosin/muuntaja), and [Jsonista](https://github.com/metosin/jsonista) that supports both
synchronous and asynchronous access patterns.

### [Full Documentation](https://cljdoc.org/d/clj-okhttp/clj-okhttp/0.1.0-SNAPSHOT)

### Philosophies

#### it's just data

clj-okhttp follows in the tradition of other Clojure http clients by accepting ring-style request maps and returning
ring-style response maps.

#### data instead of exceptions

clj-okhttp does not throw exceptions on non-2xx status codes. it just returns data and you're responsible for checking
status codes and expectations prior to using the response data. if you really want exceptions you can write your own
middleware to do that.

#### no viral asynchronous constructs

clj-okhttp uses respond/raise callbacks just like asynchronous ring instead of forcing you into an opinionated construct
like futures / manifold / core.async.

#### composable transformations

clj-okhttp uses ring-style middleware internally and allows for custom middleware functions to be used per-client
instance and per-request.

#### do the right thing by default

clj-okhttp tries to just do the right thing for you instead of requiring configuration options for common cases.

- connection pooling is automatic
- you can supply pem-encoded keys/certificates instead of messing with java crypto classes
- it decodes response data for you based on the content type of the response (by default)
- it has default read/write/connection timeouts instead of infinite timeouts like some other http clients

#### no globals or dynamic vars

clj-okhttp does not provide a default / global http client instance. You need to manage your client instances and pass
them into clj-okhttp functions as appropriate.

#### be efficient

clj-okhttp uses jsonista because it is faster than cheshire. clj-okhttp does not buffer entire responses into a byte
array like some other http clients and it supports lazy streaming serialization of request data.

### Quickstart

The main HTTP client functionality is provided by the `clj-okhttp.core` namesapce.

First, require it in the REPL:

```clojure
(require '[clj-okhttp.core :as http])
```

Or in your application:

```clojure
(ns my-app.core
  (:require [clj-okhttp.core :as http]))
```

This client supports simple `head`, `options`, `get`, `put`, `post`, `delete`, and `patch` requests.

You must create your own instance of an `OkHttpClient` to make requests. This can be done via the `create-client`
function:

```clojure
(def client (http/create-client opts))
```

You are responsible for managing these clients and reusing them. By reusing them you get things like
connection pooling for free for added performance!

### Usage

```clojure 

(require '[clj-okhttp.core :as http])

(def opts {:read-timeout 1000})

; clients contain a connection pool
(def client (http/create-client opts))

; synchronous GET that returns a data map and 
; a parsed body of data if the body was a content 
; type understood by the default muuntaja instance
(http/get client "https://httbin.org/get")

; supply a request map to customize other aspects of the request
(http/get client "https://httbin.org/get" {:query-params {:a 1}})
(http/get client "https://httpbin.org/anything" {:query-params {:a "foo" "b" ["1" "2"] :c 3}})

;; custom headers (can mix keywords and strings)
(http/get client "https://httpbin.org/anything" {:headers {"content-type" "text/plain" :accept :json}})

; make asynchronous calls by using callbacks
(def request {})
(def respond (fn [response] (println "response" response)))
(def raise   (fn [exception] (.printStackTrace exception)))
(http/get client "https://httbin.org/get" request respond raise)
```

### Creating a Client

For a full list of client options see `clj-okhttp.core/create-client`. Explanation of the parameters
can be found [here](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/).

With that being said there are a few common options to look at:

#### Connection Pool

You may want to adjust the default connection pool to increase the number of connections or adjust the keep alive.

```clojure
{:connection-pool {:max-idle-connections 5 :keep-alive-duration 5 :time-unit java.util.concurrent.TimeUnit/MINUTES}}
```

#### Redirects

By default the client will follow redirects and ssl redirects. This behavior can be adjusted if desired.

```clojure
{:follow-redirects true 
 :follow-ssl-redirects true}
```

#### Timeouts

By default the client will have a connect/read/write timeout of 10s and no call timeout.

```clojure
{:call-timeout 0 :connect-timeout 10000 :read-timeout 10000 :write-timeout 10000}
```

#### Allowing self-signed certificates

While not recommended (useful for testing), you can ignore all self signed certificates via the `:insecure?` flag.

```clojure
{:insecure? true}
```

#### Cookies

A custom `CookieJar` may be supplied. Below is an example of an in-memory CookieJar

```clojure
(def MemoryCookieJar
  (let [cache (atom [])]
    (letfn [(cookie-expired? [^Cookie cookie]
              (< (.expiresAt cookie) (System/currentTimeMillis)))
            (cookies-equal? [^Cookie cookie ^Cookie other]
              (and (= (.name cookie) (.name other))
                   (= (.domain cookie) (.domain other))
                   (= (.path cookie) (.path other))
                   (= (.secure cookie) (.secure other))
                   (= (.hostOnly cookie) (.hostOnly other))))]
      (reify CookieJar
        (^List loadForRequest [_ ^HttpUrl url]
          (->> (swap! cache #(remove cookie-expired? %))
               (filterv (fn [^Cookie cookie]
                          (.matches cookie url)))))
        (saveFromResponse [_ _ response-cookies]
          (swap! cache (fn [cookies]
                         (->> cookies
                              (remove (fn [^Cookie cookie]
                                        (some #(cookies-equal? cookie %) response-cookies)))
                              (concat response-cookies)))))))))
```

```clojure
{:cookie-jar MemoryCookieJar}
```

#### Decompression

By default OkHttp will perform transparent GZIP. It will add the appropriate `accept-encoding` header
(if not already supplied) and automatically handle decompression for you.

#### Caching

OkHttp implements an optional, off by default, Cache. OkHttp aims for RFC correct and pragmatic caching behaviour,
following common real-world browser like Firefox/Chrome and server behaviour when ambiguous. This can be configured
via:

```clojure
{:directory  "/tmp"
 :max-size    123
 :file-system okhttp3.internal.io.FileSystem/SYSTEM}
```

#### Proxy

To configure a proxy, see the `:proxy`/`:proxy-selector`/`:proxy-authenticator` options. An example of using a socks5 
proxy below:

```clojure
(let [proxy-host "proxy-host"
      proxy-port 8888
      username   "user"
      password   "pass"]
  (create-client
    {:proxy               (Proxy. Proxy$Type/SOCKS (InetSocketAddress. proxy-host proxy-port))
     :proxy-authenticator (reify Authenticator
                            (authenticate [this route response]
                              (when (and (= (str/lower-case (.getRequestingHost this))
                                            (str/lower-case proxy-host))
                                         (= (.getRequestingPort this)
                                            proxy-port))
                                (PasswordAuthentication. username (.toCharArray password)))))}))
```

#### DNS Resolution

Users may add their own DNS resolver function to override the default DNS Resolver. This is useful in situations where 
you are unable to change the name to IP Address mapping. It is analogous to the --resolve flag present in curl. Below
is an example of how to configure your own DNS resolver.

```clojure
(create-client
  {:dns (reify Dns
          (lookup [this hostname]
            ;; Or do custom logic here
            (.toList (InetAddress/getAllByName hostname))))})
```


### Output Coercion

```clojure
;; The default output is a string body
(http/get client "http://example.com/foo.txt")

;; Coerce as a byte-array
(http/get client "http://example.com/favicon.ico" {:as :byte-array})

;; Coerce as json
(http/get client "http://example.com/foo.json" {:as :json})

;; Coerce as Transit encoded JSON or MessagePack
(http/get client "http://example.com/foo" {:as :transit+json})
(http/get client "http://example.com/foo" {:as :transit+msgpack})

;; Coerce as a clojure datastructure
(http/get client "http://example.com/foo.clj" {:as :clojure})

;; Try to automatically coerce the output based on the content-type
;; header (this is currently a BETA feature!). Currently supports
;; text, json and clojure (with automatic charset detection)
;; clojure coercion requires "application/clojure" or
;; "application/edn" in the content-type header
(http/get client "http://example.com/foo.json" {:as :auto})

;; Return the body as a stream
(http/get client "http://example.com/bigrequest.html" {:as :stream})
;; Note that the connection to the server will NOT be closed until the
;; stream has been read!
```

### Link Headers

clj-okhttp parses any link headers returned in the response, and adds them to the :links key on the response map. 
This is particularly useful for paging RESTful APIs:

```clojure
(:links (http/get client "https://api.github.com/gists"))
=> {:next {:href "https://api.github.com/gists?page=2"}
    :last {:href "https://api.github.com/gists?page=22884"}}
```

### Raw Requests

A more general `request` function is also available which is useful as a primitive for building higher-level 
interfaces:

```clojure
(defn api-action [method path & [opts]]
  (http/request
    client
    (merge {:request-method method :url (str "https://httpbin.org" path)} opts)))
```

### Middleware

clj-okhttp uses ring-style middleware internally and allows for custom middleware functions to be used per-client
instance and per-request.

```clojure
(let [my-middleware [(fn [handler]
                       (fn wrap-hello
                         ([request]
                          (println "hello")
                          (handler request))
                         ([request respond raise]
                          (println "hello")
                          (handler request respond raise))))]]
  ;; Per Client
  (get (create-client {:middleware my-middleware}) "https://google.com")
  ;; Per Request
  (get client "https://google.com" {:middleware my-middleware}))

```

### Interceptors

OkHttp provides two different types of interceptors: Application and Network. You can read abouch which one to choose
[here](https://square.github.io/okhttp/features/interceptors/). For example, you can roll your own logging
interceptor

```
{:interceptors [(fn [^Interceptor$Chain chain]
                     (let [request (.request chain)
                           t1  (System/nanoTime)]
                       (printf "Sending request %s on %s%n%s"
                               (.url request)
                               (.connection chain)
                               (.headers request))
                       (let [response (.proceed chain request)
                             t2       (System/nanoTime)]
                         (printf "Received response for %s in %.1fms%n%s"
                                 (.url (.request response))
                                 (/ (- t2 t1) 1e6)
                                 (.headers response))
                         response)))]}
```

You can read more [here](https://square.github.io/okhttp/features/interceptors/#choosing-between-application-and-network-interceptors)
when deciding between application and network interceptors.

Generally speaking, it's preferred to use [middleware](#middleware) for application interceptors.

### Headers

By default, clj-okhttp will convert all headers to lower-case strings for both the request and the response.

* Request headers keys can be strings/keywords and request header values can be Dates/Instanst/strings/keywords
* Response headers will always be a map of a string to a string.
