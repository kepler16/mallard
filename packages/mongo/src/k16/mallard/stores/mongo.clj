(ns k16.mallard.stores.mongo
  (:require
   [k16.mallard.datastore :as datastore.api]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [mongo-driver-3.collection :as mongo]
   [promesa.core :as p]
   [promesa.exec :as exec]
   [taoensso.timbre :as log]
   [tick.core :as t])
  (:import
   [com.mongodb.client.result UpdateResult]))

(set! *warn-on-reflection* true)

(defn- create-lock
  "Creates a self-refreshing lock using task scheduling"
  [{:keys [mongo collection refresh-ms locked-at]}]
  (let [db (:db mongo)
        refresh-ms' (or refresh-ms 5000)
        lock (atom {:locked-at locked-at
                    :active true
                    :-task nil})

        schedule-refresh!
        (fn reschedule! []
          (let [task
                (exec/schedule!
                 refresh-ms'
                 (fn []
                   (locking lock
                     (try
                       (let [active-lock-ts (:locked-at @lock)
                             now (t/now)
                             res (mongo/update-one db collection
                                                   {:_id "lock"
                                                    :locked_at active-lock-ts}
                                                   {:$set {:locked_at now}})]

                         (when (= 0 (.getModifiedCount ^UpdateResult res))
                           (throw (ex-info "Failed to refresh lock" {})))

                         (swap! lock #(assoc % :locked-at now))
                         (reschedule!))

                       (catch Exception e
                         (log/error "Failed to refresh lock" e)
                         (swap! lock #(assoc % :active false)))))))]

            (swap! lock #(assoc % :-task task))))]

    (schedule-refresh!)
    lock))

(defn create-mongo-datastore
  "Create a MongoDB backed datastore that handles locking and log storage. Stores the state
   in a document with the id `state` and locks in a document with the id `lock`"
  [{:keys [mongo collection lock-timeout-ms] :as params}]
  (let [db (:db mongo)]
    (reify datastore.api/DataStore
      (load-state [_]
        (let [state (mongo/find-one db collection {:_id "state"})]
          (-> (m/decode datastore.api/?State state mt/json-transformer)
              (dissoc :_id))))
      (save-state! [_ state]
        (when-not (m/validate datastore.api/?State state)
          (throw (ex-info "State schema validation failed"
                          {:errors (me/humanize (m/explain datastore.api/?State state))})))
        (mongo/update-one db collection {:_id "state"}
                          {:$set state}
                          {:upsert? true}))

      (acquire-lock! [_]
        (let [timeout-ms (or lock-timeout-ms (* 10 60 1000))
              now (t/now)
              expired-ts (t/<< (t/now) (t/new-duration timeout-ms :millis))]

          (mongo/update-one db collection
                            {:_id "lock"}
                            {:$set {}}
                            {:upsert? true})

          (let [res (mongo/update-one db collection
                                      {:$and [{:_id "lock"}
                                              {:$or [{:locked_at {:$exists false}}
                                                     {:locked_at {:$lte expired-ts}}]}]}
                                      {:$set {:locked_at now}})]

            (when (= 0 (.getModifiedCount ^UpdateResult res))
              (throw (ex-info "Lock conflict. Could not acquire lock as it is already held by another process" {}))))

          (create-lock (assoc params :locked-at now))))

      (release-lock! [_ lock]
        (locking lock
          (p/cancel! (:-task @lock))
          (mongo/update-one db collection
                            {:locked_at (:locked-at @lock)}
                            {:$unset {:locked_at 1}})
          (swap! lock #(assoc % :active false)))))))

(def ?PropsSchema
  [:map
   [:collection :string]
   [:lock-timeout-ms {:optional true} :int]
   [:refresh-ms {:optional true} :int]
   [:mongo :any]])

(def component
  {:gx/start {:gx/processor (fn [{:keys [props]}]
                              (create-mongo-datastore props))
              :gx/props-schema ?PropsSchema}})
