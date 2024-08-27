(ns k16.mallard.store.mongo-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [k16.mallard.store :as mallard.store]
   [k16.mallard.store.mongo :as store.mongo]
   [k16.mallard.test.mongo :as test.mongo]
   [matcher-combinators.test]
   [promesa.core :as p]
   [tick.core :as t]))

(def ^:dynamic *mongo* nil)

(defn with-mongo [test]
  (binding [*mongo* (test.mongo/create-test-connection! "mongodb://localhost:27017")]
    (try
      (test)
      (finally
        (.drop (:db *mongo*))
        (.close (:client *mongo*))))))

(use-fixtures :each with-mongo)

(deftest mongo-datastore-state-test
  (testing "Mongo store state read/write operations"
    (let [store (store.mongo/create-datastore {:mongo *mongo*
                                               :collection "migrate"})
          op {:id "1"
              :direction :up
              :started_at (t/now)
              :finished_at (t/now)}]

      (is (nil? (mallard.store/load-state store)))
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

(deftest mongo-datastore-lock-test
  (testing "Mongo store lock acquire/release operations"
    (let [store (store.mongo/create-datastore {:mongo *mongo*
                                               :collection "migrate"
                                               :lock-timeout-ms 500
                                               :refresh-ms 100})]

      (testing "Acquiring and refreshing a lock"
        (let [lock (mallard.store/acquire-lock! store)
              locked-at (:locked-at @lock)]
          (is (:active @lock))

          (is (not (try (mallard.store/acquire-lock! store)
                        true
                        (catch Exception _ false))))

          ;; Wait for the refresh
          @(p/delay 150)

          (is (:active @lock))
          (is (not= locked-at (:locked-at @lock)))

          (testing "Releasing a lock"
            (mallard.store/release-lock! store lock)
            (is (not (:active @lock)))

            (let [new-lock (mallard.store/acquire-lock! store)]
              (is (:active @new-lock))
              (mallard.store/release-lock! store new-lock)))))

      (testing "Lock expiry"
        (let [lock (mallard.store/acquire-lock! store)
              locked-at (:locked-at @lock)]

          ;; cancel the internal task that refreshes the lock
          (p/cancel! (:-task @lock))

          (is (not (try (mallard.store/acquire-lock! store)
                        true
                        (catch Exception _ false))))

          ;; Wait for the lock to timeout. This does release it, just simulates a crashed process
          @(p/delay 500)

          (is (:active @lock))
          (is (= locked-at (:locked-at @lock)))

          (is (try (mallard.store/acquire-lock! store)
                   true
                   (catch Exception _ false))))))))
