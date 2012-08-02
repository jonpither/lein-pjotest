(defproject lein-pjotest "0.1.0"
  :description "A leiningen plugin for running test namespaces in parallel with junit output"
  :url "https://github.com/jonpither/lein-pjotest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/tools.namespace "0.1.0"]
                 [leiningen-core "2.0.0-preview7"]]
  :eval-in-leiningen true)
