(ns clj-okhttp.protocols
  (:require [clojure.java.io :as io]
            [clojure.string :as strings])
  (:import [okhttp3 Request Headers HttpUrl Request$Builder RequestBody FormBody MultipartBody Headers$Builder MultipartBody$Part MultipartBody$Builder MultipartReader$Part MultipartBody$Part$Companion FormBody$Builder Response ResponseBody HttpUrl$Builder]
           [clojure.lang IPersistentMap]
           [java.util Date]
           [java.time Instant]
           [java.io InputStream File FileInputStream FilterInputStream]
           [okio BufferedSink]))

(set! *warn-on-reflection* true)

(defn dispatch-kind
  ([v] (cond (class? v) v (sequential? v) (mapv dispatch-kind v) :otherwise (class v)))
  ([v & more] (dispatch-kind (cons v more))))

(defmulti transform #'dispatch-kind)

(defmacro deftransform [symbol dispatch-key bindings & body]
  `(do
     ; identity transform
     (defmethod ~symbol ~(vec (repeat 2 (first dispatch-key))) [~'target ~'source] ~'source)

     ; requested transform
     (defmethod ~symbol ~dispatch-key ~bindings ~@body)))

(defmethod transform :default [target source]
  (throw (ex-info (format "Don't understand how to create a %s from a %s" (dispatch-kind target) (dispatch-kind source)) {:source source :target target})))

(deftransform transform [Request IPersistentMap] [_ ^IPersistentMap map]
  (let [builder
        (doto (Request$Builder.)
          (.headers (transform Headers (:headers map {})))
          (.url ^HttpUrl (transform HttpUrl map))
          (.method (strings/upper-case (name (:request-method map :get))) (transform RequestBody map)))]
    (.build builder)))

(deftransform transform [RequestBody IPersistentMap] [_ ^IPersistentMap map]
  (cond
    (contains? map :body)
    (transform RequestBody (:body map))
    (contains? map :form-params)
    (transform FormBody (:form-params map))
    (contains? map :multipart)
    (transform MultipartBody (:multipart map))
    :otherwise nil))

(deftransform transform [RequestBody File] [_ ^File file]
  (proxy [RequestBody] []
    (contentType []
      "application/octet-stream")
    (contentLength []
      (.length file))
    (isOneShot []
      false)
    (writeTo [^BufferedSink sink]
      (with-open [in (FileInputStream. file)]
        (io/copy in (.outputStream sink))))))

(deftransform transform [RequestBody InputStream] [_ ^InputStream stream]
  (proxy [RequestBody] []
    (contentType []
      "application/octet-stream")
    (isOneShot []
      true)
    (writeTo [^BufferedSink sink]
      (with-open [in stream]
        (io/copy in (.outputStream sink))))))

(deftransform transform [MultipartBody$Part File] [_ file]
  (MultipartBody$Part/create ^RequestBody (transform RequestBody file)))

(deftransform transform [MultipartBody$Part InputStream] [_ stream]
  (MultipartBody$Part/create ^RequestBody (transform RequestBody stream)))

(deftransform transform [MultipartBody$Part IPersistentMap] [_ {:keys [name part-name content]}]
  (MultipartBody$Part/createFormData
    (or name part-name)
    (or part-name name)
    (transform RequestBody content)))

(deftransform transform [MultipartBody [IPersistentMap]] [_ parts]
  (let [builder (MultipartBody$Builder.)]
    (doseq [part parts]
      (.addPart builder ^MultipartBody$Part (transform MultipartBody$Part part)))
    (.build builder)))

(deftransform transform [FormBody IPersistentMap] [_ ^IPersistentMap map]
  (let [builder (FormBody$Builder.)]
    (doseq [[k v] map] (.add builder (name k) v))
    (.build builder)))

(deftransform transform [Headers IPersistentMap] [_ ^IPersistentMap map]
  (let [builder (Headers$Builder.)
        grouped (group-by (comp class val) map)]
    (doseq [[k v] (get grouped Date)]
      (.add builder (name k) ^Date v))
    (doseq [[k v] (get grouped Instant)]
      (.add builder (name k) ^Instant v))
    (doseq [[k v] (get grouped String)]
      (.add builder (name k) ^String v))
    (.build builder)))

(deftransform transform [HttpUrl IPersistentMap] [_ ^IPersistentMap request]
  (let [base ^HttpUrl (transform HttpUrl (:url request))]
    (if (not-empty (:query-params request))
      (let [cloned (.newBuilder base)]
        (doseq [[k v] (:query-params request)]
          (.addQueryParameter cloned (name k) (str v)))
        (.build cloned))
      base)))

(deftransform transform [HttpUrl String] [_ ^String url]
  (HttpUrl/parse url))

(deftransform transform [IPersistentMap Response] [_ ^Response response]
  {:headers (transform IPersistentMap (.headers response))
   :body    (transform InputStream (.body response))
   :status  (.code response)})

(deftransform transform [IPersistentMap Headers] [_ ^Headers headers]
  (reduce (fn [agg i] (assoc agg (.name headers i) (.value headers i))) {} (range (.size headers))))

(deftransform transform [InputStream ResponseBody] [_ ^ResponseBody body]
  (let [stream (.byteStream body)]
    (proxy [FilterInputStream] [stream]
      (close [] (.close stream) (.close body)))))
