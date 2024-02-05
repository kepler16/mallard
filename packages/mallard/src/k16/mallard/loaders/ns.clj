(ns k16.mallard.loaders.ns
  (:require
   [clojure.string :as str]))

(set! *warn-on-reflection* true)

(defn load!
  "Dynamically require all given namespaces as operation files."
  [namespaces]
  (->> namespaces
       (map (fn [ns']
              (require ns')
              {:id (-> (name ns') (str/split #"\.") last)
               :run-up! (ns-resolve ns' 'run-up!)
               :run-down! (ns-resolve ns' 'run-down!)}))))
