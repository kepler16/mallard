(ns k16.mallard.loader.ns
  (:require
   [clojure.string :as str]))

#_{:clj-kondo/ignore [:discouraged-var]}
(defmacro load!
  "Dynamically require all given namespaces as operation files."
  ([namespaces] `(load! {} ~namespaces))
  ([opts namespaces]
   (let [namespaces (if (= 'quote (first namespaces))
                      (second namespaces)
                      namespaces)]
     `(do
        (doseq [namespace# '~namespaces]
          (require namespace#))

        (mapv (fn [namespace#]
                (let [run-up# (resolve (symbol (str namespace# "/run-up!")))
                      run-down# (resolve (symbol (str namespace# "/run-down!")))]
                  (cond-> {:id (if (:use-fq-namespace ~opts)
                                 (str namespace#)
                                 (-> namespace# str (str/split #"\.") last))
                           :metadata (or (meta (the-ns namespace#)) {})
                           :run-up! run-up#}
                    run-down# (assoc :run-down! run-down#))))
              '~namespaces)))))
