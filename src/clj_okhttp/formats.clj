(ns clj-okhttp.formats
  (:require [muuntaja.parse :as parse]
            [muuntaja.core :as m]
            [clj-okhttp.okhttp :as okhttp])
  (:import [muuntaja.core FormatAndCharset]))

(set! *warn-on-reflection* true)

(let [negotiate (deref #'muuntaja.core/-negotiate-content-type)]
  (def negotiate-content-type
    (parse/fast-memoize 1000
      (fn [muuntaja content-type]
        (negotiate muuntaja content-type)))))

(defn get-intended-content-type [{:keys [multipart form-params] :as request}]
  (let [headers (get-in request [:headers])]
    (if-some [header (find headers "content-type")]
      (val header)
      (or
        (when (some? form-params) "application/x-www-form-urlencoded")
        (when (some? multipart) "multipart/form-data")
        "application/octet-stream"))))

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

(defn format-response [{:keys [muuntaja] :as request} {:keys [body] :as response}]
  (let [response-content-type (get-in response [:headers "content-type"])
        negotiated            ^FormatAndCharset
                              (negotiate-content-type muuntaja response-content-type)]
    (if-some [decoder (m/decoder muuntaja (.-format negotiated))]
      (assoc response :body (decoder body (.-charset negotiated)))
      (assoc response :body body))))

(def defaults
  (assoc m/default-options :return :output-stream))

(def muuntaja-factory
  (memoize (fn [opts] (m/create opts))))