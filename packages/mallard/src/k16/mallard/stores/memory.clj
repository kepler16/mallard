(ns k16.mallard.stores.memory
  (:require
   [k16.mallard.datastore :as datastore.api])
  (:import
   [java.util UUID]))

(set! *warn-on-reflection* true)

(defn create-memory-datastore
  "Create an in-memory datastore. This is useful for tests but should not be
   used in practice."
  []
  (let [state (atom nil)
        lock (atom nil)]
    (reify datastore.api/DataStore
      (load-state [_]
        @state)
      (save-state! [_ new-state]
        (swap! state (fn [_] new-state)))
      (acquire-lock! [_]
        (when @lock
          (throw (ex-info "A lock is already being held" {:lock @lock})))
        (swap! lock (fn [_] (.toString (UUID/randomUUID)))))
      (release-lock! [_ lock-id]
        (when (not= @lock lock-id)
          (throw (ex-info "Trying to release an unknown lock" {:lock lock-id
                                                               :current @lock})))
        (swap! lock (fn [_] nil))))))
