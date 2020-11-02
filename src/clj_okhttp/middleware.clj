(ns clj-okhttp.middleware
  (:require [clj-okhttp.protocols :as protos]
            [clj-okhttp.utilities :as utils]
            [clojure.string :as strings]
            [muuntaja.core :as m])
  (:import [clojure.lang IPersistentMap]
           [okhttp3 Request]
           [java.io InputStream]))


(set! *warn-on-reflection* true)

(defn wrap-to-and-from-data [handler]
  (fn to-and-from-data-handler
    ([request]
     (let [req-obj (protos/transform Request request)
           res-obj (handler req-obj)]
       (with-meta
         (protos/transform IPersistentMap res-obj)
         {:request req-obj :response res-obj})))
    ([request respond raise]
     (let [req-obj (protos/transform Request request)]
       (handler req-obj #(respond (with-meta
                                    (protos/transform IPersistentMap %1)
                                    {:request req-obj :response %})) raise)))))


(defn wrap-lowercase-response-headers [handler]
  (letfn [(transform [response]
            (update response :headers #(utils/map-keys strings/lower-case %)))]
    (fn lowercase-headers-handler
      ([request]
       (transform (handler request)))
      ([request respond raise]
       (handler request (comp respond transform) raise)))))


(defn wrap-decode-responses [handler]
  (fn decode-response-handler
    ([request]
     (let [muuntaja (:muuntaja request)
           response (handler (dissoc request :muuntaja))]
       (if (not= (:as request) :stream)
         (with-open [_ ^InputStream (:body response)]
           (let [original  (get-in response [:headers "content-type"])
                 augmented (update-in response [:headers "Content-Type"] #(or % original))]
             (if (some? muuntaja)
               (assoc response :body (m/decode-response-body muuntaja augmented))
               (assoc response :body (m/decode-response-body augmented)))))
         response)))
    ([request respond raise]
     (handler request
              (fn [response]
                (try
                  (respond
                    (if (not= (:as request) :stream)
                      (with-open [_ ^InputStream (:body response)]
                        (let [original  (get-in response [:headers "content-type"])
                              augmented (update-in response [:headers "Content-Type"] #(or % original))]
                          (if-some [muuntaja (:muuntaja request)]
                            (assoc response :body (m/decode-response-body muuntaja augmented))
                            (assoc response :body (m/decode-response-body augmented)))))
                      response))
                  (catch Exception e
                    (raise e))))
              raise))))


(defn wrap-encode-requests [handler]
  #_(letfn [(coerce-request [{:keys [body form-params] :as request}]
              (let [muuntaja     (:muuntaja request)
                    content-type (get-in request [:headers "content-type"])
                    content      (or body form-params)
                    new-body     (cond
                                   (nil? body)
                                   nil
                                   (or (instance? InputStream body) (bytes? body))
                                   body
                                   (string? body)
                                   (.getBytes body)
                                   :otherwise
                                   (let [response (m/encode muuntaja content-type content)]
                                     (proxy [RequestBody] []
                                       (contentLength []
                                         (if (bytes? response) (alength response) -1))
                                       (contentType []
                                         content-type)
                                       (writeTo [^BufferedSink sink]
                                         (cond
                                           (instance? IFn response)
                                           (response (.outputStream sink))
                                           (instance? InputStream response)
                                           (io/copy response (.outputStream sink))
                                           (bytes? response)
                                           (.write sink ^bytes response 0 (alength response))))
                                       (isOneShot []
                                         (and (not (bytes? response)) (not (string? response)))))))]
                (cond-> request
                  (some? new-body)
                  (assoc :body new-body)
                  :always
                  (dissoc :form-params))))]
      (fn encode-request-handler
        ([request]
         (handler (coerce-request request)))
        ([request respond raise]
         (handler (coerce-request request) respond raise))))
  handler)


(defn wrap-basic-authentication [handler]
  (fn basic-auth-handler
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))

(defn wrap-parse-link-headers [handler]
  (fn parse-links-handler
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))

(defn wrap-throw-exceptional-responses [handler]
  (fn throw-exception-responses-handler
    ([request]
     (handler request))
    ([request respond raise]
     (handler request respond raise))))

