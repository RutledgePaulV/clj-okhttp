(ns clj-okhttp.utilities
  (:require [clojure.string :as strings]))


(defn map-keys
  "Transform the keys of a map"
  [f m]
  (letfn [(f* [agg k v] (assoc! agg (f k) v))]
    (with-meta
      (persistent! (reduce-kv f* (transient (or (empty m) {})) m))
      (meta m))))

(defn join [a b]
  (case [(strings/ends-with? a "/")
         (strings/starts-with? b "/")]
    [false false] (str a "/" b)
    [true true] (str a (subs b 1))
    ([false true] [true false]) (str a b)))

(defn join-paths [& paths]
  (reduce join (flatten paths)))

(defn deep-merge [m1 m2]
  (merge-with
    #(if (and (map? %1) (map? %2))
       (deep-merge %1 %2) %2)
    m1 m2))