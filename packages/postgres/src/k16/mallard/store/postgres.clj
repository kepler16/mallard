(ns k16.mallard.store.postgres
  (:require
   [k16.mallard.store :as mallard.store]
   [malli.core :as m]
   [malli.error :as me]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time]
   [next.jdbc.result-set :as rs]
   [tick.core :as t])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defonce LOCK_ID (hash "mallard_postgres_lock"))

(def ^:private ^:sql create-history-table
  "CREATE TABLE IF NOT EXISTS %s (
     id TEXT NOT NULL,
     direction TEXT NOT NULL,
     started_at TIMESTAMP NOT NULL,
     finished_at TIMESTAMP NOT NULL
   )")

(defn- row->entry
  [{:keys [id direction started_at finished_at]}]
  {:id id
   :direction (keyword direction)
   :started_at (t/instant started_at)
   :finished_at (t/instant finished_at)})

(defn- entry->row
  [{:keys [id direction started_at finished_at]}]
  [id (name direction) started_at finished_at])

(def ^:private ^:sql insert-log-statement
  "INSERT INTO %s (id, direction, started_at, finished_at)
   VALUES (?, ?, ?, ?)")

(def ^:private ^:sql select-log-statement
  "SELECT * FROM %s
   ORDER BY started_at ASC")

(def ?Props
  [:map
   [:ds :any]
   [:table-name :string]
   [:lock-timeout-ms {:optional true} :int]
   [:refresh-ms {:optional true} :int]])

(defn create-datastore
  {:malli/schema [:-> ?Props mallard.store/?DataStore]}
  [{:keys [ds schema-name table-name]
    :or {schema-name "mallard"
         table-name "schema_history"}}]
  (let [history-table (str schema-name "." table-name)
        lock-sess-conn (atom nil)]

    (jdbc/with-transaction [tx ds]
      (jdbc/execute! tx [(format "create schema if not exists %s" schema-name)])
      (jdbc/execute! tx [(format create-history-table history-table)]))

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
              rows (map entry->row (:log state))]
          (jdbc/with-transaction [tx ds]
            (jdbc/execute! tx [(str "DELETE FROM " history-table)])
            (jdbc/execute-batch! tx statement rows {}))))

      (acquire-lock! [_]
        (let [conn (reset! lock-sess-conn (jdbc/get-connection ds))
              acquired? (-> (jdbc/execute-one!
                             conn
                             [(format
                               "select pg_try_advisory_lock(%d) as lock_acquired"
                               LOCK_ID)]
                             {:return-keys true})
                            :lock_acquired)]
          (if acquired?
            LOCK_ID
            (throw (ex-info "Failed to acquire migration lock"
                            {:lock-id LOCK_ID})))))

      (release-lock! [_ _]
        (with-open [^AutoCloseable conn @lock-sess-conn]
          (jdbc/execute! conn [(format "SELECT pg_advisory_unlock(%d)" LOCK_ID)])
          (reset! lock-sess-conn nil))))))

