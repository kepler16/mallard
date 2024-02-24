(ns k16.mallard.loaders.ns-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.mallard.loaders.ns :as loaders.ns]
   [matcher-combinators.test]))

(deftest ns-loader-test
  (testing "It should load migrations from a given collection of namespaces"
    (let [migrations (loaders.ns/load! '(fixtures.migrations.1-migration
                                         fixtures.migrations.2-migration))]
      (is (match? [{:id "1-migration"
                    :run-up! (requiring-resolve 'fixtures.migrations.1-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.1-migration/run-down!)}
                   {:id "2-migration"
                    :run-up! (requiring-resolve 'fixtures.migrations.2-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.2-migration/run-down!)}]
                  migrations)))))
