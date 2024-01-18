(ns k16.mallard.loaders.fs-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.mallard.loaders.fs :as loaders.fs]
   [matcher-combinators.test]))

(deftest fs-loader-test
  (testing "It should load migrations from disk in the correct order"
    (let [migrations (loaders.fs/load-migrations! "test/fixtures/migrations")]
      (is (match? [{:id "1-migration"
                    :run-up! (requiring-resolve 'fixtures.migrations.1-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.1-migration/run-down!)}
                   {:id "2-migration"
                    :run-up! (requiring-resolve 'fixtures.migrations.2-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.2-migration/run-down!)}]
                  migrations)))))