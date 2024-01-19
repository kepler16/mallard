(ns k16.mallard.loaders.fs
  (:require
   [clojure.core :as core]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn- file->ns
  "Extract clojure ns name from a file"
  [file]
  (->> file
       slurp
       (re-find #"^\(ns\s+([^\s);]+)")
       second
       symbol))

(defn- resolve-migration-files [dir]
  (->> (io/file dir)
       file-seq
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".clj"))
       (map (fn [file]
              {:file file
               :path (.getPath ^java.io.File file)
               :ns (file->ns file)}))
       (sort-by :path)))

(defn load-migrations!
  "Given a resource path, attempt to load all migration files found therein."
  [dir]
  (let [files (resolve-migration-files dir)]
    (->> files
         (map (fn [file]
                (let [{ns' :ns path :path} file]
                  (core/load-file path)
                  {:id (-> (name ns') (str/split #"\.") last)
                   :run-up! (ns-resolve ns' 'run-up!)
                   :run-down! (ns-resolve ns' 'run-down!)}))))))
