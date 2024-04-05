(ns k16.mallard.loaders.ns
  (:require
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defmacro load!
  "Dynamically require all given namespaces as operation files."
  [namespaces]
  `(do
     (doseq [namespace# ~namespaces]
       (require namespace#))

     (->> ~namespaces
          (map (fn [namespace#]
                 {:id (-> namespace# str (str/split #"\.") last)
                  :run-up! (resolve (symbol (str namespace# "/run-up!")))
                  :run-down! (resolve (symbol (str namespace# "/run-down!")))})))))
