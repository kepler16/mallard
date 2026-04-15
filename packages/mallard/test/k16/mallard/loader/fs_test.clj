(ns k16.mallard.loader.fs-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.mallard.loader.fs :as loader.fs]
   [matcher-combinators.test]
   [matcher-combinators.matchers :as matcher]))

(deftest fs-loader-test
  (testing "It should load migrations from disk in the correct order"
    (let [migrations (loader.fs/load! "fixtures/migrations")]
      (is (match? [{:id "1-migration"
                    :metadata {:some "metadata"}
                    :run-up! ifn?
                    :run-down! ifn?}
                   {:id "2-migration"
                    :metadata {}
                    :run-up! ifn?
                    :run-down! matcher/absent}]
                  migrations)))))
