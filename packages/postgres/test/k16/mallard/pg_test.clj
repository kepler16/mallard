(ns k16.mallard.pg-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [hikari-cp.core :as cp]
   [k16.mallard.store :as mallard.store]
   [k16.mallard.store.postgres :as store.pg]
   [k16.mallard.test.pg :as test.pg]
   [matcher-combinators.test]
   [tick.core :as t]))

(def ^:dynamic *pg* nil)

(defn with-pg [test]
  (test.pg/create-database-if-not-exists!)
  (binding [*pg* (test.pg/create-test-ds!)]
    (try
      (test)
      (finally
        (cp/close-datasource *pg*)
        (test.pg/drop-database!)))))

(use-fixtures :each with-pg)

(deftest pg-datastore-state-test
  (testing "PG store state read/write operations"
    (let [store (store.pg/create-datastore {:ds *pg*
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

(deftest pg-datastore-lock-test
  (testing "Should not allow more than one simultaneous lock"
    (let [store (store.pg/create-datastore {:ds *pg*
                                            :table-name "migration"})
          store2 (store.pg/create-datastore {:ds *pg*
                                             :table-name "migration"})]
      (testing "Aquiring lock"
        (let [lock (mallard.store/acquire-lock! store)]
          (is (boolean lock))
          (is (not (try (mallard.store/acquire-lock! store2)
                        true
                        (catch Exception _ false))))
          (mallard.store/release-lock! store lock)
          (is (boolean (mallard.store/acquire-lock! store2))))))))
