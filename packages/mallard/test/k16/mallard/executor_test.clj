(ns k16.mallard.executor-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.mallard.datastore :as datastore.api]
   [k16.mallard.executor :as executor]
   [matcher-combinators.matchers :as matchers]
   [k16.mallard.stores.memory :as stores.memory]
   [malli.util :as mu]
   [matcher-combinators.test])
  (:import
   [clojure.lang ExceptionInfo]))

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
      (is (= "Migration props are invalid" (ex-message ex)))
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
      (is (= "Migration props are invalid" (ex-message ex)))
      (is (= {:errors {:direction ["should be either :up or :down"],
                       :migrations ["should be a sequence of migrations"],
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

(deftest executor-test
  (let [store (stores.memory/create-memory-datastore)]
    (testing "Executing a single migration"
      (let [op-log (executor/execute! {:store store
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

    (testing "Executing the remaining migrations"
      (let [op-log (executor/execute! {:store store
                                       :migrations migrations
                                       :direction :up})]
        (is (= 3 (count op-log)))
        (is (= ["1" "2" "3"] (map :id op-log)))))

    (testing "Undoing the last migration"
      (let [op-log (executor/execute! {:store store
                                       :migrations migrations
                                       :direction :down
                                       :limit 1})]

        (is (= 4 (count op-log)))
        (is (= [{:id "1" :direction :up}
                {:id "2" :direction :up}
                {:id "3" :direction :up}
                {:id "3" :direction :down}]
               (map #(select-keys % [:id :direction]) op-log)))))

    (testing "Rerun the rolled back migration"
      (let [op-log (executor/execute! {:store store
                                       :migrations migrations
                                       :direction :up})]

        (is (= 5 (count op-log)))
        (is (= [{:id "1" :direction :up}
                {:id "2" :direction :up}
                {:id "3" :direction :up}
                {:id "3" :direction :down}
                {:id "3" :direction :up}]
               (map #(select-keys % [:id :direction]) op-log)))))

    (testing "Failure state for missing ref migration"
      (let [op-log (try (executor/execute! {:store store
                                            :migrations (pop migrations)
                                            :direction :up})
                        (catch Exception _ false))]

        (is (not op-log)))))

  (let [store (stores.memory/create-memory-datastore)]
    (testing "Rolling back a single migration"
      (let [migrations' (take 1 migrations)
            _ (executor/execute! {:store store
                                  :migrations migrations'
                                  :direction :up})
            _ (executor/execute! {:store store
                                  :migrations migrations'
                                  :direction :down
                                  :limit 1})
            op-log (executor/execute! {:store store
                                       :migrations migrations'
                                       :direction :up
                                       :limit 1})]

        (is (= 3 (count op-log)))
        (is (match? {:id "1"
                     :direction :up}
                    (last op-log)))))))
