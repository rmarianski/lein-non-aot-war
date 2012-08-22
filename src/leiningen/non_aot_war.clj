(ns leiningen.non-aot-war
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.data.xml :as xml]
            [leiningen.javac :as javac]
            [leiningen.ring.war :as war]
            [leiningen.ring.uberwar :as uberwar])
  (:import java.io.File))

(defn ^File servlet-file [java-source-path]
  (io/file java-source-path "nonaotwar" "NonAotServlet.java"))

(defn ^File listener-file [java-source-path]
  (io/file java-source-path "nonaotwar" "NonAotListener.java"))

(defn java-invoke [^clojure.lang.Named sym]
  (str "RT.var(\"" (namespace sym) "\", \"" (.getName sym) "\").invoke();"))

(defn listener-input [project]
  (let [^String java-src (slurp (io/resource "NonAotListener.java.template"))
        {:keys [init destroy handler resource-scripts]} (:ring project)]
    (assert resource-scripts "No resource scripts specified")
    (assert handler "No ring handler specified")
    (->
     java-src
     (.replaceFirst
      "<<clojure-loads>>"
      (string/join "\n"
                   (for [rs resource-scripts]
                     (str "RT.loadResourceScript(\"" (str rs) "\");"))))
     (.replaceFirst
      "<<handler-init>>"
      (if init (java-invoke init) ""))
     (.replaceFirst
      "<<handler-destroy>>"
      (if destroy (java-invoke destroy) "")))))

(defn servlet-input [project]
  (let [^String java-src (slurp (io/resource "NonAotServlet.java.template"))
        ^clojure.lang.Named handler (:handler (:ring project))]
    (assert handler "No ring handler specified")
    (-> java-src
        (.replaceFirst "<<handler-namespace>>" (namespace handler))
        (.replaceFirst "<<handler-name>>" (.getName handler)))))

(defn java-path [project]
  (or (first (:java-source-paths project))
      "java"))

(defn write-listener [project]
  (let [java-src-path (java-path project)
        lf (listener-file java-src-path)]
    (if (.exists lf)
      (println "Listener file already exists. Not overwriting.")
      (do
        (io/make-parents lf)
        (with-open [out (io/writer lf)]
          (io/copy (listener-input project) out))))))

(defn write-servlet [project]
  (let [java-src-path (java-path project)
        sf (servlet-file java-src-path)]
    (if (.exists sf)
      (println "Servlet file already exists. Not overwriting.")
      (do
        (io/make-parents sf)
        (with-open [out (io/writer sf)]
          (io/copy (servlet-input project) out))))))

(defn default-web-xml []
  (xml/indent-str
   (xml/sexp-as-element
    [:web-app
     [:listener
      [:listener-class "nonaotwar.NonAotListener"]]
     [:servlet
      [:servlet-name "non-aot-servlet"]
      [:servlet-class "nonaotwar.NonAotServlet"]]
     [:servlet-mapping
      [:servlet-name "non-aot-servlet"]
      [:url-pattern "/*"]]])))

(defn create-non-aot-war [project warfile-name write-fn]
  (let [project (-> project
                    war/add-servlet-dep
                    (update-in [:java-source-paths]
                               #(or % [(java-path project)]))
                    (update-in [:ring :web-xml]
                               #(or % (java.io.StringReader.
                                       (default-web-xml)))))]
    (write-listener project)
    (write-servlet project)
    (let [result (javac/javac project)]
      (when-not (and (number? result) (pos? result))
        (let [war-path (war/war-file-path project warfile-name)]
          (write-fn project war-path)
          (println "Created" war-path)
          war-path)))))

(defn war-task
  "Create a war file with a non aot compiled servlet."
  ([project] (war-task project "ROOT.war"))
  ([project warfile-name]
     (create-non-aot-war project warfile-name war/write-war)))

(defn uberwar-task
  "Create an uberwar file with a non aot compiled servlet."
  ([project] (uberwar-task project "ROOT.war"))
  ([project warfile-name]
     (create-non-aot-war project warfile-name uberwar/write-uberwar)))

(defn non-aot-war
  "Create non ahead of time compiled war files."
  ([project]
     (println "Using the uberwar subtask")
     (non-aot-war project "uberwar"))
  ([project subtask & args]
     (cond
      (= subtask "uberwar") (apply uberwar-task project args)
      (= subtask "war") (apply war-task project args)
      :else (println "Unknown subtask:" subtask
                     "Expecting war or uberwar"))))
