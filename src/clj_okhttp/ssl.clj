(ns clj-okhttp.ssl
  (:require [clojure.string :as strings])
  (:import [java.security.cert CertificateFactory Certificate]
           [java.security KeyFactory KeyStore SecureRandom]
           [javax.net.ssl TrustManagerFactory KeyManagerFactory SSLContext X509TrustManager]
           [java.security.spec PKCS8EncodedKeySpec]
           [java.util UUID Base64]
           [java.io ByteArrayInputStream]))

(defn pem-body [s]
  (strings/join ""
    (-> s
        (strings/replace #".*BEGIN\s+.*" "")
        (strings/replace #".*END\s+.*" "")
        (strings/trim)
        (strings/split-lines))))

(defn base64-string->bytes ^"[B" [^String s]
  (.decode (Base64/getDecoder) s))

(defn base64-string->stream [^String contents]
  (ByteArrayInputStream. (base64-string->bytes contents)))

(defn pem-stream [s]
  (base64-string->stream (pem-body s)))

(defonce rsa-factory
  (KeyFactory/getInstance "RSA"))

(defonce x509-factory
  (CertificateFactory/getInstance "X.509"))

(defn trust-managers [certificates]
  (let [certs (doall (for [cert (if (string? certificates) [certificates] certificates)]
                       (with-open [stream (pem-stream cert)]
                         (.generateCertificate x509-factory stream))))
        ks    (doto (KeyStore/getInstance (KeyStore/getDefaultType))
                (.load nil))
        _     (doseq [cert certs]
                (.setCertificateEntry ks (name (gensym "certificate")) cert))
        tf    (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
                (.init ks))]
    (.getTrustManagers tf)))

(defn key-managers [client-certificate client-key]
  (let [cert-chain  (with-open [stream (pem-stream client-certificate)]
                      (let [^"[Ljava.security.cert.Certificate;" ar (make-array Certificate 0)]
                        (.toArray (.generateCertificates x509-factory stream) ar)))
        private-key (with-open [stream (pem-stream client-key)]
                      (.generatePrivate rsa-factory (PKCS8EncodedKeySpec. stream)))
        password    (str (UUID/randomUUID))
        key-store   (doto (KeyStore/getInstance (KeyStore/getDefaultType))
                      (.load nil)
                      (.setKeyEntry "cert" private-key password cert-chain))
        kmf         (doto (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
                      (.init key-store password))]
    (.getKeyManagers kmf)))

(defn x509-trust-manager? [trust-manager]
  (instance? X509TrustManager trust-manager))

(defn ssl-socket-factory [trust-managers key-managers]
  (.getSocketFactory
    (doto (SSLContext/getInstance "TLS")
      (.init key-managers trust-managers (SecureRandom.)))))

