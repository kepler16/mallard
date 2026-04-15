(ns k16.mallard.loader.ns-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [k16.mallard.loader.ns :as loader.ns]
   [matcher-combinators.test]
   [matcher-combinators.matchers :as matcher]))

(deftest ns-loader-test
  (testing "It should load migrations from a given collection of namespaces"
    (let [migrations (loader.ns/load! [fixtures.migrations.1-migration
                                       fixtures.migrations.2-migration])]
      (is (match? [{:id "1-migration"
                    :metadata {:some "metadata"}
                    :run-up! ifn?
                    :run-down! ifn?}
                   {:id "2-migration"
                    :metadata {:metadata-key "value"}
                    :run-up! ifn?
                    :run-down! matcher/absent}]
                  migrations)))))

(deftest ns-loader-fqn-test
  (testing "It should load migrations using the fqn as the id"
    (let [migrations (loader.ns/load! {:use-fq-namespace true}
                                      [fixtures.migrations.1-migration
                                       fixtures.migrations.2-migration])]
      (is (match? [{:id "fixtures.migrations.1-migration"
                    :metadata {:some "metadata"}
                    :run-up! ifn?
                    :run-down! ifn?}
                   {:id "fixtures.migrations.2-migration"
                    :metadata {:metadata-key "value"}
                    :run-up! ifn?
                    :run-down! matcher/absent}]
                  migrations)))))
