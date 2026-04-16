(ns k16.mallard.test.pg
  (:require
   [hikari-cp.core :as cp]
   [next.jdbc :as jdbc]))

(defonce DB_NAME "mallard_pg_test")

(defn database-exists?
  "Checks if a database exists.
   Args:
     ds-config: Datasource config (connected to an existing database)
   Returns:
     true if exists, false otherwise"
  [ds-config]
  (let [ds (jdbc/get-datasource ds-config)]
    (-> (jdbc/execute-one!
         ds
         [(format "SELECT 1 FROM pg_database WHERE datname = '%s'" DB_NAME)]
         {:return-keys true})
        boolean)))

(defn create-database!
  "Creates a new PostgreSQL database.
   Args:
     ds-config: Datasource config map (host, port, user, password, dbname)
   Returns:
     true if successful, throws on failure"
  [ds-config]
  (let [ds (jdbc/get-datasource ds-config)]
    (try
      (jdbc/execute! ds [(format "CREATE DATABASE %s" DB_NAME)] {:timeout 5000})
      true
      (catch Exception e
        (throw e)))))

(def base-ds-config
  {:dbtype "postgresql"
   :host "localhost"
   :port 5432
   :dbname "postgres"
   :user "postgres"
   :password "postgres"})

(defn create-database-if-not-exists!
  "Creates a database if it doesn't exist.
   Returns:
     true if created or already exists, throws on other errors"
  []
  (if (database-exists? base-ds-config)
    true
    (create-database! base-ds-config)))

(def test-ds-config {:auto-commit true
                     :read-only false
                     :connection-timeout 30000
                     :validation-timeout 5000
                     :idle-timeout 600000
                     :max-lifetime 1800000
                     :minimum-idle 10
                     :maximum-pool-size 10
                     :pool-name "db-pool"
                     :adapter "postgresql"
                     :username "postgres"
                     :password "postgres"
                     :database-name DB_NAME
                     :server-name "localhost"
                     :port-number 5432
                     :register-mbeans false})

(defn create-test-ds! []
  (cp/make-datasource test-ds-config))

(defn drop-database! []
  (let [ds (jdbc/get-datasource base-ds-config)]
    (try
      (jdbc/execute! ds [(format "DROP DATABASE %s" DB_NAME)] {:timeout 5000})
      true
      (catch Exception e
        (throw e)))))


