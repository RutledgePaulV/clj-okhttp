(ns clj-okhttp.muuntaja
  (:require [muuntaja.parse :as parse]
            [muuntaja.core :as m]
            [clojure.java.io :as io])
  (:import [okhttp3 RequestBody MediaType]
           [okio BufferedSink]
           [clojure.lang IFn]
           [java.io InputStream]
           [muuntaja.core FormatAndCharset]))

(set! *warn-on-reflection* true)

(let [negotiate (deref #'muuntaja.core/-negotiate-content-type)]
  (def negotiate-content-type
    (parse/fast-memoize 1000
      (fn [muuntaja content-type]
        (negotiate muuntaja content-type)))))


(defn format-request [{:keys [muuntaja form-params body] :as request}]
  (let [request-content-type (get-in request [:headers "content-type"])
        negotitated          ^FormatAndCharset (negotiate-content-type muuntaja request-content-type)
        request-body         (if (some? request-content-type)
                               (let [response (m/encode muuntaja
                                                        (.-format negotitated)
                                                        body
                                                        (.-charset negotitated))]
                                 (proxy [RequestBody] []
                                   (contentLength []
                                     (if (bytes? response)
                                       (alength ^bytes response)
                                       -1))
                                   (contentType []
                                     (MediaType/parse request-content-type))
                                   (writeTo [^BufferedSink sink]
                                     (cond
                                       ; this is the most memory efficient
                                       ; encoding since malli does a true
                                       ; streaming encoding in this case only
                                       (instance? IFn response)
                                       (response (.outputStream sink))
                                       ; this should be the fastest for small
                                       ; payloads when you can afford the memory
                                       (bytes? response)
                                       (.write sink ^bytes response (int 0) (alength ^bytes response))
                                       ; even though this is a stream, malli
                                       ; uses a byte array input stream so
                                       ; you're paying the full memory cost. note
                                       ; that this is malli's default mode!
                                       (instance? InputStream response)
                                       (io/copy response (.outputStream sink))))
                                   (isOneShot []
                                     (not (bytes? response)))))
                               nil)]
    (assoc request :body request-body)))

(defn format-response [{:keys [muuntaja] :as request} {:keys [body] :as response}]
  (let [response-content-type (get-in response [:headers "content-type"])
        negotitated           ^FormatAndCharset (negotiate-content-type muuntaja response-content-type)
        response-body         (m/decode muuntaja (.-format negotitated) body (.-charset negotitated))]
    (assoc response :body response-body)))

(def defaults
  (assoc m/default-options :return :output-stream))

(def muuntaja-factory
  (memoize (fn [opts] (m/create opts))))