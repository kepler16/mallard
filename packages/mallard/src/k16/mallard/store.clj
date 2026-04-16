(ns k16.mallard.store
  (:import
   [java.time Instant]
   [java.util Date]))

(def ^:private ?instant
  [:fn {:encode/json #(Instant/.toString %)
        :decode/json (fn -decode-instant [value]
                       (cond
                         (string? value) (Instant/parse value)
                         (instance? Date value) (Date/.toInstant value)

                         :else (throw (ex-info "Unsupported time value"
                                               {:value value}))))

        :error/message "Should be an #instant"}
   (fn -instant? [value]
     (instance? Instant value))])

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
  "Protocol for a data store that can hold the migration log and optionally
   provide a locking mechanism"
  (load-state [store]
    "Load the migration state from the underlying datastore")
  (save-state! [store state]
    "Save the migration state to the underlying datastore")

  (acquire-lock! [store]
    "Acquire a lock which should remain active while the migrations are being
     executed")
  (release-lock! [store lock]
    "Release a previously acquired lock"))

(def ?DataStore
  [:fn {:error/message "should implement the DataStore protocol"}
   (fn -datastore? [value]
     (satisfies? DataStore value))])
