(defproject clj-okhttp/clj-okhttp "0.1.0-SNAPSHOT"

  :description
  "A fast and lightweight clojure http client constructed on top of OkHttp."

  :url
  "https://github.com/rutledgepaulv/clj-okhttp"

  :license
  {:name "MIT License" :url "http://opensource.org/licenses/MIT" :year 2020 :key "mit"}

  :scm
  {:name "git" :url "https://github.com/rutledgepaulv/clj-okhttp"}

  :pom-addition
  [:developers
   [:developer
    [:name "Paul Rutledge"]
    [:url "https://github.com/rutledgepaulv"]
    [:email "rutledgepaulv@gmail.com"]
    [:timezone "-5"]]]

  :deploy-repositories
  [["releases" :clojars]
   ["snapshots" :clojars]]

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [metosin/muuntaja "0.6.7"]
   [metosin/jsonista "0.2.7"]
   [com.squareup.okhttp3/okhttp "4.9.0"]]

  :profiles
  {:dev {:resource-paths ["testfiles"]}}

  :plugins
  [[lein-cloverage "1.1.2"]]

  :repl-options
  {:init-ns clj-okhttp.core})
