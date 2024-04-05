(ns k16.mallard.api
  (:require
   [k16.mallard.executor :as executor]
   [k16.mallard.loaders.fs :as loaders.fs]))

(set! *warn-on-reflection* true)

(defn run-up!
  "Execute all unapplied operations"
  [props]
  (executor/execute! (assoc props :direction :up)))

(defn run-down!
  "Rollback all applied operations"
  [props]
  (executor/execute! (assoc props :direction :down)))

(defn run-next!
  "Run the next unapplied operation"
  [props]
  (executor/execute! (merge props {:direction :up
                                   :limit 1})))

(defn undo!
  "Rollback the last applied operation"
  [props]
  (executor/execute! (merge props {:direction :down
                                   :limit 1})))

(defn redo!
  "Reapply the last applied operation. This will roll it back first"
  [props]
  (undo! props)
  (run-next! props))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn run
  "A run function to be used in a Deps.edn project to execute operations using the file loader.
   
   :init-store! - Should be given a symbol that resolves to a datastore init function.
   :load-dir    - should be a resource path to a directory containing operation files that will
                  be loaded using the file loader.
   :operations  - Should be a symbol resolving to a set of operations
   :action      - should be given an action to perform. One of #{:up :down :next :undo :redo}"
  [{create-ctx-fn :create-ctx!
    create-store-fn :create-store!
    shutdown-fn :shutdown!
    load-dir :load-dir
    operations :operations
    action :action}]

  (let [create-store! (requiring-resolve create-store-fn)
        shutdown! (when shutdown-fn (requiring-resolve shutdown-fn))
        context (when-let [create-ctx (some-> create-ctx-fn (requiring-resolve))]
                  (create-ctx))
        store (create-store! context)
        props {:context context
               :operations (or operations
                               (loaders.fs/load! load-dir))
               :store store}]

    (case action
      :up (run-up! props)
      :down (run-down! props)
      :next (run-next! props)
      :undo (undo! props)
      :redo (redo! props))

    (when shutdown!
      (try
        (shutdown! context store)
        (catch Exception _ (System/exit 1))
        (finally (System/exit 0))))))
