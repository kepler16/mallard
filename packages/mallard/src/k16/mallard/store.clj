(ns k16.mallard.store
  (:require
   [tick.core :as t]))

(set! *warn-on-reflection* true)

(def ^:private inst-codec
  {:encode/json #(.toString ^java.time.Instant %)
   :decode/json #(t/instant %)})

(def ^:private ?instant
  [:fn (merge inst-codec {:error/message "Should be an #instant"}) t/instant?])

(def ?Direction
  [:enum :up :down])

(def ?OpLogEntry
  [:map {:closed true}
   [:id :string]
   [:direction ?Direction]
   [:metadata {:optional true}
    [:map {:closed false}]]
   [:started_at ?instant]
   [:finished_at ?instant]])

(def ?State
  [:map
   [:log {:description "A log of all operations that have been executed"}
    [:sequential ?OpLogEntry]]])

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

