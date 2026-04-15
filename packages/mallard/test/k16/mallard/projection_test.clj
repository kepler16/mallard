(ns k16.mallard.projection-test
  (:require
   [clojure.test :refer [deftest is]]
   [k16.mallard.log :as mallard.log]
   [matcher-combinators.test]))

(def ^:private now
  (java.time.Instant/now))

(def ^:private base
  {:metadata {}
   :started_at now
   :finished_at now})

(def migrations
  [(assoc base
          :id "1"
          :direction :up)
   (assoc base
          :id "1"
          :direction :down)
   (assoc base
          :id "1"
          :direction :up)
   (assoc base
          :id "3"
          :direction :up)
   (assoc base
          :id "4"
          :direction :up)])

(deftest oplog-projection-test
  (is (= [(assoc base :id "1")
          (assoc base :id "3")
          (assoc base :id "4")]
         (mallard.log/project migrations))))

(deftest corrupted-state
  (let [ex (try (mallard.log/project
                 [{:id "1" :direction :up}
                  {:id "missing" :direction :down}])
                nil
                (catch Exception e e))]
    (is (instance? Exception ex))
    (is (= "Error reprocessing oplog. A :down operation did not follow an :up operation of the same id"
           (ex-message ex)))))
