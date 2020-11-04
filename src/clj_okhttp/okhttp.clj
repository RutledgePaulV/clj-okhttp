(ns clj-okhttp.okhttp
  (:require [clojure.string :as strings])
  (:import [okhttp3 HttpUrl Headers$Builder Request Request$Builder Headers Response ResponseBody]
           [java.time Instant]
           [java.util Date]
           [java.io FilterInputStream InputStream]
           [clojure.lang IPersistentMap]))

(set! *warn-on-reflection* true)

(defn ->url ^HttpUrl [url query-params]
  (let [[^HttpUrl http-url segments]
        (cond
          (string? url)
          [(HttpUrl/parse url) []]
          (vector? url)
          (let [[begin & parts] (flatten url)]
            [(HttpUrl/parse begin) parts]))]
    (if (or (not-empty segments) (not-empty query-params))
      (let [builder (.newBuilder http-url)]
        (doseq [segment segments]
          (.addPathSegment builder (if (keyword? segment) (name segment) (str segment))))
        (doseq [[k v] query-params]
          (.addQueryParameter builder (name k) (if (keyword? v) (name v) (str v))))
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
  (.build
    (doto (Request$Builder.)
      (.method (strings/upper-case (name request-method)) body)
      (.headers headers)
      (.url ^HttpUrl url))))

(defn <-response [^Response response]
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