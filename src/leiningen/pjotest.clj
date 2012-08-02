(ns leiningen.pjotest
  (:use
   [clojure.java.io]
   [clojure.test :only (*test-out* successful?)]
   [clojure.test.junit]
   [clojure.tools.namespace])
  (:require [leiningen.core.classpath :as classpath]
            [leiningen.core.user]
            [leiningen.core.eval :as eval]
            [leiningen.test :as test]
            [bultitude.core :as b]))

(defn- run-tests-fn-form [report]
  `(fn [t#]
     (try
;;       (require t# :reload-all) test-out has this
       (with-open [file-stream# (java.io.FileWriter.
                                 (clojure.java.io/file ~report (str t# ".xml")))]
         (binding [clojure.test/*test-out* file-stream#]
           (time (do
                   (let [result# (clojure.test.junit/with-junit-output
                                   (clojure.test/run-tests t#))
                         result# (dissoc result# :type)]
                     (println (ns-name t#) ":" result#)
                     result#)))))
       (catch Throwable e#
         (clojure.test/is false (format "Uncaught exception: %s" e#))
         (System/exit 1)))))

(defn- run-tests-form [report test-nses prefix]
  `(do
     (if ~prefix
       (create-ns 'prefix))
     (let [test-nses# (map symbol (.split ~test-nses " "))]
       (apply require :reload-all test-nses#)
       (let [results# (->> (if ~prefix (filter #(re-find (re-pattern ~prefix) (str %))
                                               (all-ns))
                               test-nses#)
                           (pmap ~(run-tests-fn-form report))
                           (apply merge-with +))]
         (spit (clojure.java.io/file ~report (str "test.out")) results#)))
     (System/exit 0)))

;; TODO this doesn't work, requiring the munge-proj
(defn- require-clojure-test-form []
  `(do
     (require 'clojure.test)
     (require 'clojure.test.unit)))

(defn- munge-proj [p]
  (assoc (update-in p [:injections] concat
                    ['(require 'clojure.tools.namespace)
                     '(require 'clojure.test.junit)
                     '(require 'clojure.test)
                     '(require 'clojure.java.io)])
    :eval-in :subprocess))

(defn- run-test-suite [project report prefix]
  (let [project (munge-proj project)
        test-nses (clojure.string/join " " (b/namespaces-on-classpath :classpath (map file (:test-paths project))))]
    (eval/eval-in-project project (run-tests-form report test-nses prefix))
    (read-string (slurp (file report "test.out")))))

(defn pjotest
  "Running tests in parallel with junit xml output"
  [project & opts]
  (let [opts (apply hash-map opts)
        report-dir (or (opts "-report-dir") "reports")
        prefix (opts "-prefix")]
  (try
    (.mkdirs (file report-dir))
    (let [result (run-test-suite project report-dir prefix)]
      (assert (successful? result)))
    (finally
     (shutdown-agents)))))