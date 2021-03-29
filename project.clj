(defproject clj-okhttp/clj-okhttp "0.1.0"

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
   [metosin/muuntaja "0.6.8"]
   [metosin/jsonista "0.3.1"]
   [com.squareup.okhttp3/okhttp "4.9.1"]]

  :profiles
  {:test {:resource-paths ["testfiles"]
          :dependencies   [[clj-http "3.12.1"]
                           [cheshire "5.10.0"]
                           [criterium "0.4.6"]
                           [org.slf4j/slf4j-simple "1.7.30"]
                           [org.testcontainers/testcontainers "1.15.2"]]}}

  :cloverage
  {:selector [:coverage]}

  :test-selectors
  {:coverage (complement :performance)}

  :plugins
  [[lein-cloverage "1.1.2"]]

  :repl-options
  {:init-ns clj-okhttp.core})
