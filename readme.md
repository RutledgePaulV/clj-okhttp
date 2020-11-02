
### What

A Clojure http client based on ok-http, muuntaja, and jsonista. Also provides a simple websocket client.

### Why

* clj-http is a heavyweight client with too many features that show its age
  * reliance on dynamic vars / functions
  * uses cheshire instead of jsonista
  * defaults to infinite connection / socket timeouts
  * closing a stream doesn't close the upstream connection until it's been fully drained
    
* http-kit has design flaws that make it incompatible with large streaming responses
  
* java-http-clj is very close to what I want
   * I think callbacks are the best async interface for a library, that way users can
     adapt into whatever other monadic thing they like.
   * I wanted oob support for middleware and request/response serialization/deserialization.
  