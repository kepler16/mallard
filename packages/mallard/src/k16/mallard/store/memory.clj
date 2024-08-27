(ns k16.mallard.store.memory
  (:require
   [k16.mallard.store :as mallard.store]))

(defn create-datastore
  "Create an in-memory datastore.

  This is useful for tests but should not be used in a real application."
  []
  (let [state (atom nil)
        lock (atom nil)]
    (reify mallard.store/DataStore
      (load-state [_]
        @state)
      (save-state! [_ new-state]
        (swap! state (fn [_] new-state)))
      (acquire-lock! [_]
        (when @lock
          (throw (ex-info "A lock is already being held" {:lock @lock})))
        (swap! lock (fn [_] (str (random-uuid)))))
      (release-lock! [_ lock-id]
        (when (not= @lock lock-id)
          (throw (ex-info "Trying to release an unknown lock" {:lock lock-id
                                                               :current @lock})))
        (swap! lock (fn [_] nil))))))
