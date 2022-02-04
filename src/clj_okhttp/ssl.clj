(ns clj-okhttp.ssl
  (:require [clojure.string :as strings]
            [clojure.java.io :as io])
  (:import [java.security.cert CertificateFactory Certificate X509Certificate]
           [java.security KeyFactory KeyStore SecureRandom]
           [javax.net.ssl TrustManagerFactory KeyManagerFactory SSLContext X509TrustManager TrustManager KeyManager]
           [java.util UUID Base64]
           [java.io ByteArrayInputStream IOException ByteArrayOutputStream]
           [java.security.spec RSAPrivateCrtKeySpec PKCS8EncodedKeySpec]))

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

(defn pem-stream ^ByteArrayInputStream [s]
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

(defn parse-der [^bytes bites]
  (loop [fields [] stream (ByteArrayInputStream. bites)]
    (let [tag (.read stream)]
      (if-not (neg? tag)
        (let [length
              (let [length-of-length (.read stream)]
                (if-not (neg? length-of-length)
                  (if (zero? (bit-and-not length-of-length 0x7F))
                    length-of-length
                    (let [buf-length (bit-and length-of-length 0x7F)
                          buffer     (byte-array buf-length)]
                      (when (< (.read stream buffer) buf-length)
                        (throw (IOException. "Invalid DER.")))
                      (.intValue (BigInteger. 1 buffer))))
                  (throw (IOException. "Invalid DER."))))
              buffer
              (byte-array length)]
          (when (< (.read stream buffer) length)
            (throw (IOException. "Invalid DER.")))
          (recur (conj fields buffer) stream))
        fields))))

(defn decode-pkcs1 [^bytes client-key-bites]
  (let [content (parse-der client-key-bites)
        [_
         modulus
         public-exponent private-exponent
         prime-p prime-q
         prime-exponent-p prime-exponent-q
         crt-coefficient]
        (map #(BigInteger. ^bytes %) (parse-der (first content)))
        spec    (RSAPrivateCrtKeySpec.
                  modulus
                  public-exponent private-exponent
                  prime-p prime-q
                  prime-exponent-p prime-exponent-q
                  crt-coefficient)]
    (.generatePrivate rsa-factory spec)))

(defn decode-pkcs8 [^bytes client-key-bites]
  (let [spec (PKCS8EncodedKeySpec. client-key-bites)]
    (.generatePrivate rsa-factory spec)))

(defn stream->bytes [stream]
  (with-open [in stream out (ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn decode-private-key [client-key]
  (cond
    (bytes? client-key)
    (try
      (decode-pkcs8 client-key)
      (catch Exception e
        (decode-pkcs1 client-key)))
    (string? client-key)
    (recur (stream->bytes (pem-stream client-key)))))

(defn key-managers [client-certificate client-key]
  (let [cert-chain  (with-open [stream (pem-stream client-certificate)]
                      (let [^"[Ljava.security.cert.Certificate;" ar (make-array Certificate 0)]
                        (.toArray (.generateCertificates x509-factory stream) ar)))
        private-key (decode-private-key client-key)
        password    (.toCharArray (str (UUID/randomUUID)))
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
      (.init (into-array KeyManager key-managers)
             (into-array TrustManager trust-managers)
             (SecureRandom.)))))

(def trust-all-certs-trust-manager
  (reify X509TrustManager
    (checkClientTrusted [_ _chain _authType])
    (checkServerTrusted [_ _chain _authType])
    (getAcceptedIssuers [_]
      (into-array X509Certificate []))))

(def trust-all-certs-ssl-socket-factory
  (ssl-socket-factory [trust-all-certs-trust-manager] nil))
