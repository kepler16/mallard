(ns k16.mallard.test.mongo
  (:require
   [mongo-driver-3.client :as mongo.client]))

(defn create-test-connection! [uri]
  (let [client (mongo.client/create uri)
        db-name (str "mallard-test-" (str (random-uuid)))
        db (mongo.client/get-db client db-name)]
    {:db db
     :client client}))
