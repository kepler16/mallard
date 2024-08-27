(ns k16.mallard.example.main
  (:require
   [k16.mallard.example.migrate :as migrate])
  (:gen-class))

(defn -main [& args]
  (when (= "migrate" (first args))
    (migrate/run-migrations (rest args))
    (System/exit 0))

  (println "Running application"))
