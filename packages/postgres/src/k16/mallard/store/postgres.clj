(ns k16.mallard.store.postgres
  (:require
   [jsonista.core :as json]
   [k16.mallard.store :as mallard.store]
   [malli.core :as m]
   [malli.error :as me]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [tick.core :as t])
  (:import
   [java.lang AutoCloseable]
   [org.postgresql.util PGobject]))

(set! *warn-on-reflection* true)

(def ^:private ^:sql create-history-table
  "CREATE TABLE IF NOT EXISTS %s (
     id text NOT NULL,
     direction text NOT NULL,
     metadata jsonb,
     started_at timestamp NOT NULL,
     finished_at timestamp NOT NULL
   );")

(def ^:private ^:sql add-metadata-column
  "ALTER TABLE %s
     ADD COLUMN IF NOT EXISTS metadata jsonb")

(defn- row->entry
  [{:keys [id direction metadata started_at finished_at]}]
  (cond-> {:id id
           :direction (keyword direction)
           :started_at (t/instant started_at)
           :finished_at (t/instant finished_at)}
    metadata
    (assoc :metadata (json/read-value (PGobject/.getValue metadata)
                                      json/keyword-keys-object-mapper))))

(defn- metadata->pgobject ^PGobject [m]
  (let [object (PGobject.)]
    (PGobject/.setType object "jsonb")
    (PGobject/.setValue object (json/write-value-as-string m))
    object))

(defn- entry->row
  [{:keys [id direction metadata started_at finished_at]}]
  [id
   (name direction)
   (when (seq metadata)
     (metadata->pgobject metadata))
   (java.sql.Timestamp/from ^java.time.Instant started_at)
   (java.sql.Timestamp/from ^java.time.Instant finished_at)])

(def ^:private ^:sql insert-log-statement
  "INSERT INTO %s (id, direction, metadata, started_at, finished_at)
     VALUES (?, ?, ?, ?, ?)")

(def ^:private ^:sql select-log-statement
  "SELECT
     *
   FROM
     %s
   ORDER BY
     started_at ASC")

(def ?Props
  [:map
   [:ds :any]
   [:schema-name {:optional true} :string]
   [:table-name :string]
   [:lock-timeout-ms {:optional true} :int]
   [:refresh-ms {:optional true} :int]])

(defn create-datastore
  {:malli/schema [:-> ?Props mallard.store/?DataStore]}
  [{:keys [ds schema-name table-name]
    :or {schema-name "mallard"}}]
  (let [history-table (str schema-name "." table-name)]
    (jdbc/with-transaction [tx ds]
      (jdbc/execute! tx [(format "CREATE SCHEMA IF NOT EXISTS %s" schema-name)])
      (jdbc/execute! tx [(format create-history-table history-table)])
      (jdbc/execute! tx [(format add-metadata-column history-table)]))

    (reify mallard.store/DataStore
      (load-state [_]
        (let [entries
              (into []
                    (map row->entry)
                    (jdbc/plan ds
                               [(format select-log-statement history-table)]
                               {:builder-fn rs/as-unqualified-lower-maps}))]
          {:log entries}))

      (save-state! [_ state]
        (when-not (m/validate mallard.store/?State state)
          (throw (ex-info "State schema validation failed"
                          {:errors (me/humanize
                                    (m/explain mallard.store/?State state))})))

        (let [statement (format insert-log-statement history-table)
              rows (mapv entry->row (:log state))]
          (jdbc/with-transaction [tx ds]
            (jdbc/execute! tx [(str "DELETE FROM" " " history-table)])
            (jdbc/execute-batch! tx statement rows {}))))

      (acquire-lock! [_]
        (let [conn (jdbc/get-connection ds)
              lock (hash (str history-table "_lock"))
              acquired? (-> (jdbc/execute-one!
                             conn
                             [(format
                               "select pg_try_advisory_lock(%d) as lock_acquired"
                               lock)]
                             {:return-keys true})
                            :lock_acquired)]
          (if acquired?
            conn
            (throw (ex-info "Failed to acquire migration lock"
                            {:lock-id lock})))))

      (release-lock! [_ lock-conn]
        (AutoCloseable/.close lock-conn)))))
