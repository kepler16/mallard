(ns k16.mallard.loaders.fs
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

(defn- resolve-migration-files [dir]
  (->> (or (io/resource dir)
           (io/file dir))
       io/file
       file-seq
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".clj"))
       (map (fn [file]
              {:path (.getPath ^java.io.File file)
               :namespace (file->ns file)}))
       (sort-by :path)
       vec))

(defmacro load-migration-files!
  "Given a file or resource directory path attempt to load all files found within as migrations.

  This is implemented as a macro to allow preloading migrations during native-image compilation. This
  also allows loading of migrations when they are bundled as resources within a jar as the full resource
  paths are known up front."
  [dir]
  (let [namespaces (resolve-migration-files dir)]
    `(do (doseq [namespace# ~namespaces]
           (require (symbol namespace#)))

         (->> ~namespaces
              (map (fn [namespace#]
                     {:id (-> namespace# (str/split #"\.") last)
                      :run-up! (resolve (symbol (str namespace# "/run-up!")))
                      :run-down! (resolve (symbol (str namespace# "/run-down!")))}))))))
