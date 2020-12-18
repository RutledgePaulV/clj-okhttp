[![travisci](https://travis-ci.com/rutledgepaulv/clj-okhttp.svg?branch=master)](https://travis-ci.com/rutledgepaulv/clj-okhttp)
[![clojars](https://img.shields.io/clojars/v/clj-okhttp/clj-okhttp.svg)](https://clojars.org/clj-okhttp)
[![codecov](https://codecov.io/gh/rutledgepaulv/clj-okhttp/branch/master/graph/badge.svg)](https://codecov.io/gh/rutledgepaulv/clj-okhttp)
[![cljdoc](https://cljdoc.org/badge/clj-okhttp/clj-okhttp)](https://cljdoc.org/d/clj-okhttp/clj-okhttp/0.1.0-SNAPSHOT)

### What

A Clojure http and websocket client built on [OkHttp](https://github.com/square/okhttp),
[Muuntaja](https://github.com/metosin/muuntaja), and [Jsonista](https://github.com/metosin/jsonista). Supports
synchronous or asynchronous access patterns.

Largely follows the style of other http libraries like clj-http but does not attempt to be a perfect drop-in
replacement. Supports per-client-instance and per-request middleware.

### Why

I have tried pretty much every http client in the Clojure ecosystem and couldn't find anything that satisfied everything
I want. [hato](https://github.com/gnarroway/hato) comes darn close but it's only Java 11+, uses cheshire, and I prefer a
more explicit public API. I always had a pleasant experience with OkHttp in a past life when I primarily wrote Java.


### [Documentation](https://cljdoc.org/d/clj-okhttp/clj-okhttp/0.1.0-SNAPSHOT)

