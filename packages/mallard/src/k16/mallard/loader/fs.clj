(ns k16.mallard.loader.fs
  (:require
   [clojure.core :as core]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- file->ns
  "Extract clojure ns name from a file"
  [file]
  (->> (slurp file)
       (re-find #"^\(ns\s+([^\s);]+)")
       second))

(defn resolve-operation-files [dir]
  (->> (or (io/resource dir)
           (io/file dir))
       io/file
       file-seq
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".clj"))
       (map file->ns)
       sort
       vec))

(defmacro load!
  "Given a file or resource directory path attempt to load all files found within as operations.

  This is implemented as a macro to allow preloading operations during native-image compilation. This
  also allows loading of operations when they are bundled as resources within a jar as the full resource
  paths are known up front."
  [dir]
  (let [namespaces (try (resolve-operation-files dir)
                        (catch Exception _))]
    `(let [namespaces# (or ~namespaces
                           (resolve-operation-files ~dir))]
       (doseq [namespace# namespaces#]
         (require (symbol namespace#)))

       (->> namespaces#
            (map (fn [namespace#]
                   {:id (-> namespace# (str/split #"\.") last)
                    :metadata (or (meta (the-ns (symbol namespace#))) {})
                    :run-up! (resolve (symbol (str namespace# "/run-up!")))
                    :run-down! (resolve (symbol (str namespace# "/run-down!")))}))))))
