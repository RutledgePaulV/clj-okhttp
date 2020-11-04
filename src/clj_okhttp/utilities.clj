(ns clj-okhttp.utilities
  (:import [java.util Base64]))

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
