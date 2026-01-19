(ns k16.mallard.loader.ns-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.mallard.loader.ns :as loader.ns]
   [matcher-combinators.test]))

(deftest ns-loader-test
  (testing "It should load migrations from a given collection of namespaces"
    (let [migrations (loader.ns/load! [fixtures.migrations.1-migration
                                       fixtures.migrations.2-migration])]
      (is (match? [{:id "1-migration"
                    :metadata {:some "metadata"}
                    :run-up! (requiring-resolve 'fixtures.migrations.1-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.1-migration/run-down!)}
                   {:id "2-migration"
                    :metadata {}
                    :run-up! (requiring-resolve 'fixtures.migrations.2-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.2-migration/run-down!)}]
                  migrations)))))

(deftest ns-loader-fqn-test
  (testing "It should load migrations using the fqn as the id"
    (let [migrations (loader.ns/load! {:use-fq-namespace true}
                                      [fixtures.migrations.1-migration
                                       fixtures.migrations.2-migration])]
      (is (match? [{:id "fixtures.migrations.1-migration"
                    :metadata {:some "metadata"}
                    :run-up! (requiring-resolve 'fixtures.migrations.1-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.1-migration/run-down!)}
                   {:id "fixtures.migrations.2-migration"
                    :metadata {}
                    :run-up! (requiring-resolve 'fixtures.migrations.2-migration/run-up!)
                    :run-down! (requiring-resolve 'fixtures.migrations.2-migration/run-down!)}]
                  migrations)))))
