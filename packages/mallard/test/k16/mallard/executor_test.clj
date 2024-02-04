(ns k16.mallard.executor-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [k16.mallard.datastore :as datastore.api]
   [k16.mallard.executor :as executor]
   [k16.mallard.stores.memory :as stores.memory]
   [matcher-combinators.test]
   [taoensso.timbre :as log]
   [tick.core :as t])
  (:import
   [clojure.lang ExceptionInfo]))

(defn- disable-logs [test]
  (log/set-config! {})
  (test))

(use-fixtures :once disable-logs)

(def migrations
  [{:id "1"
    :run-up! (fn [_])
    :run-down! (fn [_])}
   {:id "2"
    :run-up! (fn [_])
    :run-down! (fn [_])}
   {:id "3"
    :run-up! (fn [_])
    :run-down! (fn [_])}])

(deftest executor-props-validation-test
  (testing "Executor should thrown an exception with explanation"
    (let [ex (try (executor/execute! {:some :props}) (catch Exception e e))]
      (is (= ExceptionInfo (type ex)))
      (is (= "Invalid arguments provided" (ex-message ex)))
      (is (= {:errors {:direction ["missing required key"],
                       :migrations ["missing required key"],
                       :store ["missing required key"]}}
             (ex-data ex))))

    (let [ex (try (executor/execute! {:migrations :wrong
                                      :direction :left
                                      :store "incorrect store"
                                      :limit 0})
                  (catch Exception e e))]

      (is (= ExceptionInfo (type ex)))
      (is (= "Invalid arguments provided" (ex-message ex)))
      (is (= {:errors {:direction ["should be either :up or :down"],
                       :migrations ["should be a sequence of operations"],
                       :store ["should Implement DataStore protocol"],
                       :limit ["should be at least 1"]}}
             (ex-data ex))))))

(deftest executor-context-test
  (testing "Context is passed to runers"
    (let [context {:hello "world"}
          store (stores.memory/create-memory-datastore)
          migs [{:id "1"
                 :run-up! (fn [ctx]
                            (is (= ctx context)))
                 :run-down! (fn [ctx]
                              (is (= ctx context)))}
                {:id "2"
                 :run-up! (fn [ctx]
                            (is (= ctx context)))
                 :run-down! (fn [ctx]
                              (is (= ctx context)))}]]
      (executor/execute! {:store store
                          :context context
                          :direction :up
                          :migrations migs})
      (executor/execute! {:store store
                          :context context
                          :direction :down
                          :migrations migs}))))

(deftest single-execution-test
  (let [store (stores.memory/create-memory-datastore)
        op-log (executor/execute! {:store store
                                   :migrations migrations
                                   :direction :up
                                   :limit 1})]

    (is (= 1 (count op-log)))
    (is (match? [{:id "1"
                  :direction :up
                  :started_at inst?
                  :finished_at inst?}]
                op-log))

    (is (= {:log op-log} (datastore.api/load-state store)))))

(deftest multi-execution-test
  (let [store (stores.memory/create-memory-datastore)
        op-log (executor/execute! {:store store
                                   :migrations migrations
                                   :direction :up})]

    (is (= 3 (count op-log)))
    (is (= ["1" "2" "3"] (map :id op-log)))))

(deftest down-migration-test
  (let [store (stores.memory/create-memory-datastore)]

    (datastore.api/save-state! store {:log [{:id "1"
                                             :direction :up
                                             :started_at (t/now)
                                             :finished_at (t/now)}]})

    (testing "Undoing the last migration"
      (let [op-log (executor/execute! {:store store
                                       :migrations migrations
                                       :direction :down
                                       :limit 1})]

        (is (= 2 (count op-log)))
        (is (= [{:id "1" :direction :up}
                {:id "1" :direction :down}]
               (map #(select-keys % [:id :direction]) op-log)))))))

(deftest rerun-down-migration-test
  (let [store (stores.memory/create-memory-datastore)]

    (datastore.api/save-state! store {:log [{:id "1"
                                             :direction :up
                                             :started_at (t/now)
                                             :finished_at (t/now)}
                                            {:id "1"
                                             :direction :down
                                             :started_at (t/now)
                                             :finished_at (t/now)}]})

    (testing "Rerunning the last migration"
      (let [op-log (executor/execute! {:store store
                                       :migrations migrations
                                       :direction :up
                                       :limit 1})]

        (is (= 3 (count op-log)))
        (is (= [{:id "1" :direction :up}
                {:id "1" :direction :down}
                {:id "1" :direction :up}]
               (map #(select-keys % [:id :direction]) op-log)))))))

(deftest run-up-out-of-order-test
  (let [store (stores.memory/create-memory-datastore)]

    (datastore.api/save-state! store {:log [{:id "1"
                                             :direction :up
                                             :started_at (t/now)
                                             :finished_at (t/now)}
                                            {:id "3"
                                             :direction :up
                                             :started_at (t/now)
                                             :finished_at (t/now)}]})

    (let [op-log (executor/execute! {:store store
                                     :migrations migrations
                                     :direction :up
                                     :limit 1})]

      (is (= 3 (count op-log)))
      (is (= [{:id "1" :direction :up}
              {:id "3" :direction :up}
              {:id "2" :direction :up}]
             (map #(select-keys % [:id :direction]) op-log))))))

(deftest run-down-missing-migration-test
  (let [store (stores.memory/create-memory-datastore)]

    (datastore.api/save-state! store {:log [{:id "1"
                                             :direction :up
                                             :started_at (t/now)
                                             :finished_at (t/now)}]})

    (let [ex (try (executor/execute! {:store store
                                      :migrations []
                                      :direction :down
                                      :limit 1})
                  (catch Exception e e))]

      (is (instance? Exception ex)))))
