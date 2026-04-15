(ns k16.mallard.store.sqlite
  (:require
   [jsonista.core :as json]
   [k16.mallard.store :as mallard.store]
   [malli.core :as m]
   [malli.error :as me]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [tick.core :as t]))

(set! *warn-on-reflection* true)

(def ^:private ^:sql log-table-schema
  "CREATE TABLE IF NOT EXISTS %s (
     id text NOT NULL,
     direction text NOT NULL,
     metadata text,
     started_at DATETIME NOT NULL,
     finished_at DATETIME NOT NULL
   );")

(def ^:private ^:sql lock-table-schema
  "CREATE TABLE IF NOT EXISTS %s (
     id text PRIMARY KEY,
     locked_at DATETIME
   );")

(defn- row->entry
  [{:keys [id direction metadata started_at finished_at]}]
  (cond-> {:id id
           :direction (keyword direction)
           :started_at (t/instant started_at)
           :finished_at (t/instant finished_at)}
    metadata
    (assoc :metadata (json/read-value metadata
                                      json/keyword-keys-object-mapper))))

(defn- entry->row
  [{:keys [id direction metadata started_at finished_at]}]
  [id
   (name direction)
   (when (seq metadata)
     (json/write-value-as-string metadata))
   (str started_at)
   (str finished_at)])

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
   [:db :any]
   [:table-name :string]
   [:lock-timeout-ms {:optional true} :int]
   [:refresh-ms {:optional true} :int]])

(defn- has-column? [db table column]
  (some #(= column (:name %))
        (jdbc/execute! db [(format "PRAGMA table_info(%s)" table)]
                       {:builder-fn rs/as-unqualified-lower-maps})))

(defn create-datastore
  {:malli/schema [:-> ?Props mallard.store/?DataStore]}
  [{:keys [db table-name]}]
  (let [log-table (str table-name "_log")
        lock-table (str table-name "_lock")]

    (jdbc/execute! db [(format log-table-schema log-table)])
    (jdbc/execute! db [(format lock-table-schema lock-table)])

    (when-not (has-column? db log-table "metadata")
      (jdbc/execute! db [(format "ALTER TABLE %s
                                    ADD COLUMN metadata text" log-table)]))

    (reify mallard.store/DataStore
      (load-state [_]
        (let [entries
              (into []
                    (map row->entry)
                    (jdbc/plan db
                               [(format select-log-statement log-table)]
                               {:builder-fn rs/as-unqualified-lower-maps}))]
          {:log entries}))

      (save-state! [_ state]
        (when-not (m/validate mallard.store/?State state)
          (throw (ex-info "State schema validation failed"
                          {:errors (me/humanize (m/explain mallard.store/?State state))})))

        (let [statement (format insert-log-statement log-table)
              rows (mapv entry->row (:log state))]
          (jdbc/with-transaction [tx db]
            (jdbc/execute! tx [(str "DELETE FROM" " " log-table)])
            (jdbc/execute-batch! tx statement rows {}))))

      (acquire-lock! [_])

      (release-lock! [_ _lock]))))
