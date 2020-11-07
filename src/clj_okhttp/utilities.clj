(ns clj-okhttp.utilities
  (:import [java.util Base64]
           [okhttp3 HttpUrl]))

(set! *warn-on-reflection* true)

(defn map-keys
  "Transform the keys of a map"
  [f m]
  (letfn [(f* [agg k v] (assoc! agg (f k) v))]
    (with-meta
      (persistent! (reduce-kv f* (transient (or (empty m) {})) m))
      (meta m))))

(defn basic-auth [username password]
  (let [bites
        (.encode
          (Base64/getEncoder)
          (.getBytes (str username ":" password)))]
    (str "Basic " (String. bites "UTF-8"))))

(defn url->origin [^HttpUrl url]
  (if
    (or (and (= (.scheme url) "https") (== (.port url) 443))
        (and (= (.scheme url) "http") (== (.port url) 80)))
    (format "%s://%s" (.scheme url) (.host url))
    (format "%s://%s:%d" (.scheme url) (.host url) (.port url))))