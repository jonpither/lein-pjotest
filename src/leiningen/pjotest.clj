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
            [bultitude.core :as b]
            [robert.hooke]))

;; taken from leiningen.test
(defn- form-for-hook-selectors [selectors]
  `(when (seq ~selectors)
     (robert.hooke/add-hook
      (resolve 'clojure.test/test-var)
      (fn test-var-with-selector [test-var# var#]
        (when (reduce #(or %1 (%2 (assoc (meta var#) ::var var#)))
                      false ~selectors)
          (test-var# var#))))))

(defn- run-tests-fn-form [report & [selectors]]
  `(fn [t#]
     (try
       ~(form-for-hook-selectors selectors)
       (with-open [file-stream# (java.io.FileWriter.
                                 (clojure.java.io/file ~report (str t# ".xml")))]
         (time (let [error-out# (new java.io.StringWriter)
                     result# (binding [clojure.test/*test-out* file-stream#
                                        *out* file-stream#
                                       clojure.test/*error-out* error-out#]
                                (clojure.test.junit/with-junit-output
                                  (clojure.test/run-tests t#)))
                     result# (dissoc result# :type)]
                 (println (ns-name t#) ":" result#)
                 (when-let [error# (not-empty (str error-out#))]
                   (println error#))
                 result#)))
       (catch Throwable e#
         (clojure.test/is false (format "Uncaught exception: %s" e#))
         (.printStackTrace e#)
         (System/exit 1)))))

(defn- override-junit-error-report []
  `(let [existing# (:error (methods clojure.test.junit/junit-report))]
     (defmethod clojure.test.junit/junit-report :error [m#]
       (do
         (.write clojure.test/*error-out*
                 (if (instance? Throwable (:actual m#))
                   (with-out-str (clojure.stacktrace/print-cause-trace (:actual m#)
                                                                       clojure.test/*stack-trace-depth*))
                   (:actual m#)))
         (existing# m#)))))

(defn- run-tests-form [report test-nses prefix selector]
  `(do
     ;;fugly
     (intern (the-ns (symbol "clojure.test")) (symbol "*error-out*") nil)
     (.setDynamic (var clojure.test/*error-out*) true)
     ~(override-junit-error-report)
     (if ~prefix
       (create-ns 'prefix))
     (let [test-nses# (map symbol (.split ~test-nses " "))]
       (apply require :reload test-nses#)
       (let [results# (->> (if ~prefix (filter #(re-find (re-pattern ~prefix) (str %))
                                               (all-ns))
                               test-nses#)
                           (pmap ~(run-tests-fn-form report (vector selector)))
                           (apply merge-with +))]
         (spit (clojure.java.io/file ~report (str "test.out")) results#)))
     (System/exit 0)))

;; TODO this doesn't work, requiring the munge-proj
(defn- require-clojure-test-form []
  `(do
     (require 'clojure.test)
     (require 'clojure.test.unit)))

(defn- munge-deps [p]
  (update-in p [:dependencies] #(vec (conj %
                                           ['org.clojure/tools.namespace "0.1.0"]
                                           ['robert/hooke "1.1.2"]))))

(defn- munge-eval-in [p]
  (assoc p :Eval-in :subprocess))

(defn- munge-injections [p]
  (update-in p [:injections] concat
                    ['(require 'clojure.tools.namespace)
                     '(require 'clojure.test.junit)
                     '(require 'clojure.test)
                     '(require 'clojure.java.io)
                     '(require 'clojure.stacktrace)
                     '(require 'robert.hooke)
                     ]))
                 ;;    '(activate)]))

(defn- munge-proj [p]
  (->> p munge-deps munge-eval-in munge-injections))

(defn- run-test-suite [project report prefix selector]
  (let [project (munge-proj project)
        test-nses (clojure.string/join " " (b/namespaces-on-classpath :classpath (map file (:test-paths project))))]
    (eval/eval-in-project project (run-tests-form report test-nses prefix selector))
    (read-string (slurp (file report "test.out")))))

(defn pjotest
  "Running tests in parallel with junit xml output"
  [project & opts]
  (let [opts (apply hash-map opts)
        report-dir (or (opts "-report-dir") "reports")
        prefix (opts "-prefix")
        test-selectors (merge {:all '(constantly true)} (:test-selectors project))
        selector-name (when-let [opt (opts "-selector")] (read-string opt))
        selector (some #(test-selectors %) [selector-name :default :all])]
    (try
    (.mkdirs (file report-dir))
    (let [result (run-test-suite project report-dir prefix selector)]
      (println "Totals:" result)
      (System/exit (if (successful? result) 0 1)))
    (finally
     (shutdown-agents)))))