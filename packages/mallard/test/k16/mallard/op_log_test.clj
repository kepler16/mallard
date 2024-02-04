(ns k16.mallard.op-log-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.mallard.executor :as executor]
   [matcher-combinators.test]))

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

(deftest empty-op-log
  (is (= {:unapplied [{:id "1" :operation (first migrations)}
                      {:id "2" :operation (second migrations)}
                      {:id "3" :operation (nth migrations 2)}]
          :applied []}
         (#'k16.mallard.executor/derive-active-state [] migrations))))

(deftest single-applied
  (is (= {:applied [{:id "1" :operation (first migrations)}]
          :unapplied [{:id "2" :operation (second migrations)}
                      {:id "3" :operation (nth migrations 2)}]}
         (#'k16.mallard.executor/derive-active-state [{:id "1" :direction :up}] migrations))))

(deftest out-of-order-applied
  (is (= {:applied [{:id "2" :operation (second migrations)}]
          :unapplied [{:id "1" :operation (first migrations)}
                      {:id "3" :operation (nth migrations 2)}]}
         (#'k16.mallard.executor/derive-active-state [{:id "2" :direction :up}] migrations))))

(deftest missing-applied-migration
  (is (= {:applied [{:id "missing" :operation nil}]
          :unapplied [{:id "1" :operation (nth migrations 0)}
                      {:id "2" :operation (nth migrations 1)}
                      {:id "3" :operation (nth migrations 2)}]}
         (#'k16.mallard.executor/derive-active-state [{:id "missing" :direction :up}] migrations))))

(deftest complex-projection
  (is (= {:applied [{:id "1" :operation (nth migrations 0)}
                    {:id "2" :operation (nth migrations 1)}]

          :unapplied [{:id "3" :operation (nth migrations 2)}]}
         (#'k16.mallard.executor/derive-active-state [{:id "1" :direction :up}
                                                      {:id "2" :direction :up}
                                                      {:id "2" :direction :down}
                                                      {:id "2" :direction :up}

                                                      {:id "3" :direction :up}
                                                      {:id "3" :direction :down}] migrations))))

(deftest corrupted-state
  (let [ex (try
             (#'k16.mallard.executor/derive-active-state [{:id "1" :direction :up}
                                                          {:id "missing" :direction :down}] migrations)
             nil
             (catch Exception e e))]
    (is (instance? Exception ex))))
