[![Build Status](https://travis-ci.com/rutledgepaulv/clj-okhttp.svg?branch=master)](https://travis-ci.com/rutledgepaulv/clj-okhttp)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/clj-okhttp.svg)](https://clojars.org/org.clojars.rutledgepaulv/clj-okhttp)
[![codecov](https://codecov.io/gh/rutledgepaulv/clj-okhttp/branch/master/graph/badge.svg)](https://codecov.io/gh/rutledgepaulv/clj-okhttp)

### What

A Clojure http and websocket client leveraging [OkHttp](https://github.com/square/okhttp), 
[Muuntaja](https://github.com/metosin/muuntaja), and [Jsonista](https://github.com/metosin/jsonista). 
Supports synchronous or asynchronous access patterns but does always use blocking io under the hood 
(per Square's implementation of OkHttp).

Largely follows the style of other http libraries like clj-http but does not attempt to be a drop-in 
replacement. Supports per-client-instance and per-request middleware.

### Why

I have tried pretty much every http client in the Clojure ecosystem and couldn't find anything that satisfied everything
I want. [hato](https://github.com/gnarroway/hato) comes pretty close but it's only Java 11+, uses cheshire, and I prefer 
a more explicit public API.

- Support for true raw response streaming. 
- Stop reading bytes from the server if I close the response stream before reading all content.
- Support for websocket connections.
- Support request/response encoding and decoding supporting json, edn, and transit.
- Request encoding shouldn't retain lazy sequences.
- Don't use exceptions for non-2xx status codes.
- Simple connection pooling.
- Lightweight dependencies.

