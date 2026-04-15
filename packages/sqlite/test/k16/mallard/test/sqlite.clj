(ns k16.mallard.test.sqlite
  (:require
   [next.jdbc :as jdbc]))

(defn create-test-ds! []
  (jdbc/get-connection {:dbtype "sqlite"
                        :dbname ":memory:"}))
