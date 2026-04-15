(ns k16.mallard.store.sqlite-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [k16.mallard.store :as mallard.store]
   [k16.mallard.store.sqlite :as store.sqlite]
   [k16.mallard.test.sqlite :as test.sqlite]
   [matcher-combinators.test]
   [next.jdbc :as jdbc]
   [tick.core :as t]))

(def ^:dynamic *db* nil)

(defn with-db [test]
  (let [db (test.sqlite/create-test-ds!)]
    (try
      (binding [*db* db]
        (test))
      (finally
        (.close ^java.sql.Connection db)))))

(use-fixtures :each with-db)

(deftest sqlite-datastore-state-test
  (testing "SQLite store state read/write operations"
    (let [store (store.sqlite/create-datastore {:db *db*
                                                :table-name "migration"})
          op {:id "1"
              :direction :up
              :started_at (t/now)
              :finished_at (t/now)}]

      (is (empty? (:log (mallard.store/load-state store))))
      (mallard.store/save-state! store {:log [op]})

      (let [state (mallard.store/load-state store)]
        (is (= 1 (count (:log state))))
        (is (match? (dissoc op :started_at :finished_at) (-> state :log first))))

      (mallard.store/save-state! store {:log [op (assoc op :id "2")]})

      (let [state (mallard.store/load-state store)]
        (is (= 2 (count (:log state))))
        (is (match? (dissoc op :started_at :finished_at) (-> state :log first)))
        (is (match? (-> op (assoc :id "2") (dissoc :started_at :finished_at))
                    (-> state :log second)))))))

(deftest sqlite-datastore-metadata-test
  (testing "SQLite store metadata round-trip"
    (let [store (store.sqlite/create-datastore {:db *db*
                                                :table-name "migration_meta"})
          op-with-meta {:id "1"
                        :direction :up
                        :metadata {:some-key "some-value"
                                   :nested {:a 1}}
                        :started_at (t/now)
                        :finished_at (t/now)}
          op-without-meta {:id "2"
                           :direction :up
                           :started_at (t/now)
                           :finished_at (t/now)}]

      (mallard.store/save-state! store {:log [op-with-meta op-without-meta]})

      (let [state (mallard.store/load-state store)
            entries (:log state)]
        (is (= 2 (count entries)))
        (is (= {:some-key "some-value" :nested {:a 1}}
               (:metadata (first entries))))
        (is (nil? (:metadata (second entries))))))))

(deftest sqlite-datastore-metadata-migration-test
  (testing "Existing tables without metadata column are migrated"
    (let [table-name "legacy_migration_log"]
      (jdbc/execute! *db* [(str "CREATE TABLE" " " table-name
                                " (id TEXT NOT NULL,"
                                "  direction TEXT NOT NULL,"
                                "  started_at DATETIME NOT NULL,"
                                "  finished_at DATETIME NOT NULL)")])

      (jdbc/execute! *db* [(str "INSERT INTO" " " table-name
                                " (id, direction, started_at, finished_at)"
                                " VALUES (?, ?, ?, ?)")
                           "1" "up"
                           (str (t/now)) (str (t/now))])

      (let [store (store.sqlite/create-datastore {:db *db*
                                                  :table-name "legacy_migration"})
            state (mallard.store/load-state store)]
        (is (= 1 (count (:log state))))
        (is (match? {:id "1" :direction :up} (first (:log state))))
        (is (nil? (:metadata (first (:log state)))))

        (mallard.store/save-state! store
                                   {:log [(assoc (first (:log state))
                                                 :metadata {:migrated true})]})

        (let [updated (mallard.store/load-state store)]
          (is (= {:migrated true} (:metadata (first (:log updated))))))))))
