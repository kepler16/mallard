(ns k16.mallard.example.migrate
  (:require
   [clojure.java.io :as io]
   [k16.mallard :as mallard]
   [k16.mallard.loader.fs :as loader.fs]
   [k16.mallard.store.sql :as store.sql]
   [taoensso.timbre :as log]
   [next.jdbc :as jdbc])
  (:gen-class))

(log/set-min-level! :info)

(def ^:private migrations
  (loader.fs/load! "k16/mallard/example/migrations"))

(defn- create-context []
  (io/make-parents "data/store.db")
  (let [datasource (jdbc/get-datasource
                    {:dbtype "sqlite"
                     :dbname "data/store.db"})]
    {:db datasource}))

(defn run-migrations [args]
  (let [context (create-context)
        datastore (store.sql/create-sqlite-datastore
                   {:db (:db context)
                    :table-name "migrations"})]
    (mallard/run {:context context
                  :store datastore
                  :operations migrations}
                 args)))

(defn -main [& args]
  (run-migrations args))
