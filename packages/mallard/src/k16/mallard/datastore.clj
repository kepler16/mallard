(ns k16.mallard.datastore
  (:require
   [tick.core :as t]))

(def inst-codec
  {:encode/json #(.toString %)
   :decode/json #(t/instant %)})

(def ?instant
  [:fn (merge inst-codec {:error/message "Should be an #instant"}) t/instant?])

(def ?Direction
  [:or [:= :up] [:= :down]])

(def ?Operation
  [:map {:closed true}
   [:id :string]
   [:direction ?Direction]
   [:started_at ?instant]
   [:finished_at ?instant]])

(def ?State
  [:map
   [:log {:description "A log of all migration operations that have been executed"}
    [:sequential ?Operation]]])

(defprotocol DataStore
  "Protocol for a data store that can hold the migration log and
   optionally provide a locking mechanism"
  (load-state [store]
    "Load the migration state from the underlying datastore")
  (save-state! [store state]
    "Save the migration state to the underlying datastore")

  (acquire-lock! [store]
    "Acquire a lock which should remain active while the migrations are being executed")
  (release-lock! [store lock]
    "Release a previously acquired lock"))

(def ?DataStore
  [:fn {:error/message "should Implement DataStore protocol"}
   (partial satisfies? DataStore)])
