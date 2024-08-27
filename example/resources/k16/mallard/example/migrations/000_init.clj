(ns k16.mallard.example.migrations.000-init
  (:require
   [next.jdbc :as jdbc]))

(def ^:private ^:sql users-table
  "CREATE TABLE users (
     id TEXT NOT NULL,
     name TEXT NOT NULL,
     created_at DATETIME NOT NULL
   )")

(defn run-up! [context]
  (jdbc/execute! (:db context) [users-table]))

(defn run-down! [context]
  (jdbc/execute! (:db context) ["DROP TABLE users"]))
