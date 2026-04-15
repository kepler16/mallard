(ns k16.mallard.reconciliation-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.mallard.log :as mallard.log]
   [matcher-combinators.test]))

(def operations
  [{:id "1"
    :run-up! (fn [_])
    :run-down! (fn [_])}
   {:id "2"
    :run-up! (fn [_])
    :run-down! (fn [_])}

   {:id "3"
    :run-up! (fn [_])
    :run-down! (fn [_])}])

(deftest empty-oplog
  (is (= {:unapplied [{:id "1" :operation (first operations)}
                      {:id "2" :operation (second operations)}
                      {:id "3" :operation (nth operations 2)}]
          :applied []}
         (mallard.log/reconcile-operations [] operations))))

(deftest single-applied
  (is (= {:applied [{:id "1" :operation (first operations)}]
          :unapplied [{:id "2" :operation (second operations)}
                      {:id "3" :operation (nth operations 2)}]}
         (mallard.log/reconcile-operations [{:id "1"
                                             :direction :up}]
                                           operations))))

(deftest out-of-order-applied
  (is (= {:applied [{:id "2" :operation (second operations)}]
          :unapplied [{:id "1" :operation (first operations)}
                      {:id "3" :operation (nth operations 2)}]}
         (mallard.log/reconcile-operations [{:id "2"
                                             :direction :up}]
                                           operations))))

(deftest missing-applied-migration
  (is (= {:applied [{:id "missing" :operation nil}]
          :unapplied [{:id "1" :operation (nth operations 0)}
                      {:id "2" :operation (nth operations 1)}
                      {:id "3" :operation (nth operations 2)}]}
         (mallard.log/reconcile-operations [{:id "missing"
                                             :direction :up}]
                                           operations))))

(deftest complex-projection
  (is (= {:applied [{:id "1" :operation (nth operations 0)}
                    {:id "2" :operation (nth operations 1)}]

          :unapplied [{:id "3" :operation (nth operations 2)}]}
         (mallard.log/reconcile-operations [{:id "1" :direction :up}
                                            {:id "2" :direction :up}
                                            {:id "2" :direction :down}
                                            {:id "2" :direction :up}

                                            {:id "3" :direction :up}
                                            {:id "3" :direction :down}]
                                           operations))))
