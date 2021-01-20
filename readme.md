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

; make asynchronous calls by using callbacks
(def request {})
(def respond (fn [response] (println "response" response)))
(def raise   (fn [exception] (.printStackTrace exception)))
(http/get client "https://httbin.org/get" request respond raise)

```

