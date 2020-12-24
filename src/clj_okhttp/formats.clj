(ns clj-okhttp.formats
  (:require [muuntaja.parse :as parse]
            [muuntaja.core :as m]
            [clj-okhttp.okhttp :as okhttp])
  (:import [muuntaja.core FormatAndCharset]))

(set! *warn-on-reflection* true)

(def negotiate-content-type
  (parse/fast-memoize 1000
    (fn [muuntaja content-type]
      (#'muuntaja.core/-negotiate-content-type muuntaja content-type))))

(defn get-intended-content-type [{:keys [multipart form-params headers]}]
  (if-some [header (find headers "content-type")]
    (val header)
    (or
      (when (some? form-params) "application/x-www-form-urlencoded")
      (when (some? multipart) "multipart/form-data")
      "application/octet-stream")))

(defn format-request [{:keys [muuntaja multipart form-params body] :as request}]
  (let [content-type (get-intended-content-type request)
        request-body (or (when (some? multipart)
                           (okhttp/->request-body content-type multipart))
                         (when (some? form-params)
                           (okhttp/->request-body content-type form-params))
                         (when (some? body)
                           (okhttp/->request-body content-type body))
                         (when (some? body)
                           (let [negotiated
                                 ^FormatAndCharset
                                 (negotiate-content-type muuntaja content-type)]
                             (if-some [encoder (m/encoder muuntaja (.-format negotiated))]
                               (okhttp/->request-body content-type (encoder body (.-charset negotiated)))
                               (throw (ex-info (str "No muuntaja encoder defined for " content-type) {}))))))]
    (cond-> request
      (some? request-body) (assoc :body request-body)
      (some? content-type) (assoc-in [:headers "content-type"] content-type))))

(defn resolve-content-type [content-type]
  (case content-type
    :stream "application/octet-stream"
    :csv "text/csv"
    :json "application/json"
    :yaml "application/yaml"
    :edn "application/edn"
    :clojure "application/edn"
    :transit "application/transit+json"
    (or content-type "application/octet-stream")))

(defn format-stream [muuntaja content-type stream]
  (let [resolved   (resolve-content-type content-type)
        negotiated ^FormatAndCharset (negotiate-content-type muuntaja resolved)]
    (if-some [decoder (m/decoder muuntaja (.-format negotiated))]
      (decoder stream (.-charset negotiated))
      stream)))

(def defaults
  (assoc m/default-options :return :output-stream))

(def muuntaja-factory
  (memoize (fn [opts] (m/create opts))))