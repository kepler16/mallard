(ns k16.mallard.loader.fs
  (:require
   [clojure.core :as core]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.io File]))

(set! *warn-on-reflection* true)

(defn- file->ns
  "Extract clojure ns name from a file"
  [file]
  (->> (slurp file)
       (re-find #"^\(ns\s+([^\s);]+)")
       second))

(defn- resolve-operation-files [dir]
  (->> (or (io/resource dir)
           (io/file dir))
       io/file
       file-seq
       (filterv #(File/.isFile %))
       (filterv #(str/ends-with? (File/.getName %) ".clj"))
       (mapv file->ns)
       sort
       vec))

#_{:clj-kondo/ignore [:discouraged-var]}
(defmacro load!
  "Load all operation files found at a given file or resource URL.

   Expects files to be Clojure namespaces with exported `run-up!` and optionally
   `run-down!` functions.

   Any metadata on the operation namespace will be loaded and included in the
   result.

   ```clojure
   (ns com.example.migrations.init-db
     {:some-key \"some-value\"})

   (defn run-up! [context]
     ...)

   ;; Optional
   (defn run-down! [context]
     ...)
   ```

   This is implemented in such a way as to allow being used within GraalVM
   native-image compiled applications.

   When used in this context, make sure to call it in the root of a loaded
   namespace.

   ```clojure
   (ns com.example.migrate
     (:require
      [k16.mallard.loader.fs :as loader.fs]))

   ;; Will be properly loaded and compiled into your native-image
   (def migrations
     (loader.fs/load! \"com/example/migrations\"))
   ```"
  [dir]
  (let [namespaces (try (#'k16.mallard.loader.fs/resolve-operation-files dir)
                        (catch Exception _))]
    `(let [namespaces# (or ~namespaces
                           (#'k16.mallard.loader.fs/resolve-operation-files ~dir))]
       (doseq [namespace# namespaces#]
         (require (symbol namespace#)))

       (mapv (fn [namespace#]
               (let [run-up# (resolve (symbol (str namespace# "/run-up!")))
                     run-down# (resolve (symbol (str namespace# "/run-down!")))]
                 (cond-> {:id (-> namespace# (str/split #"\.") last)
                          :metadata (or (meta (the-ns (symbol namespace#))) {})
                          :run-up! run-up#}
                   run-down# (assoc :run-down! run-down#))))
             namespaces#))))
