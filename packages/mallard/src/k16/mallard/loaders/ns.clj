(ns k16.mallard.loaders.ns
  (:require
   [clojure.string :as str]))

(defn load-migrations!
  "Dynamically require all given namespaces as migration files."
  [namespaces]
  (->> namespaces
       (map (fn [ns']
              (require ns')
              {:id (-> (name ns') (str/split #"\.") last)
               :run-up! (ns-resolve ns' 'run-up!)
               :run-down! (ns-resolve ns' 'run-down!)}))))
