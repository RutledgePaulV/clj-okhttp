(ns clj-okhttp.utilities
  (:require [clojure.string :as strings])
  (:import [java.util Base64 Base64$Encoder Map List]
           [okhttp3 HttpUrl]
           [java.lang.ref ReferenceQueue WeakReference]
           [java.util.concurrent ConcurrentHashMap]
           [java.util.function Function]
           (clojure.lang Named)))

(set! *warn-on-reflection* true)

(defn map-keys
  "Transform the keys of a map"
  [f m]
  (letfn [(f* [agg k v] (assoc! agg (f k) v))]
    (with-meta
      (persistent! (reduce-kv f* (transient (or (empty m) {})) m))
      (meta m))))

(defprotocol IntoPaths
  (->paths [x path]))

(extend-protocol IntoPaths
  Map
  (->paths [x path]
    (mapcat
      (fn [[k v]]
        (->paths v ((fnil conj []) path k)))
      x))
  List
  (->paths [x path]
    (mapcat (fn [x] (->paths x path)) x))
  Object
  (->paths [x path]
    [[path x]]))

(defn ->string [x]
  (if (instance? Named x) (name x) (str x)))

(defn flatten-query-params [params]
  (->> (->paths params [])
       (reduce
         (fn [agg [path value]]
           (update agg path (fnil conj []) value))
         {})
       (reduce-kv
         (fn [agg [top & more] v]
           (if (empty? more)
             (assoc agg (->string top) (map ->string v))
             (assoc agg (str (->string top) "[" (strings/join "][" (map ->string more)) "]") (map ->string v))))
         {})))

(defn basic-auth [username password]
  (let [bites
        (.encode ^Base64$Encoder
                 (Base64/getEncoder)
                 (.getBytes ^String (str username ":" password)))]
    (str "Basic " (String. ^bytes bites "UTF-8"))))

(defn url->origin [^HttpUrl url]
  (if
    (or (and (= (.scheme url) "https") (== (.port url) 443))
        (and (= (.scheme url) "http") (== (.port url) 80)))
    (format "%s://%s" (.scheme url) (.host url))
    (format "%s://%s:%d" (.scheme url) (.host url) (.port url))))

(defn weakly-memoize-by-key
  ([f] (weakly-memoize-by-key f vec))
  ([f cache-key-fn]
   (let [ref-queue (ReferenceQueue.)
         container (ConcurrentHashMap.)]
     (fn [& args]
       (let [generator (reify Function
                         (apply [this cache-key]
                           (let [x (apply f args)]
                             (loop []
                               (when-some [item (.poll ref-queue)]
                                 (.remove container item)
                                 (recur)))
                             x)))
             ref       (proxy [WeakReference]
                              [(apply cache-key-fn args) ref-queue]
                         (equals [that]
                           (and (instance? WeakReference that)
                                (if-some [ref (.get ^WeakReference this)]
                                  (= (.get ^WeakReference that) ref)
                                  (identical? this that))))
                         (hashCode []
                           (if-some [ref (.get ^WeakReference this)]
                             (.hashCode ref)
                             (System/identityHashCode this))))]
         (.computeIfAbsent container ref generator))))))