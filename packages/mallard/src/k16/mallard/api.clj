(ns k16.mallard.api
  (:require
   [k16.mallard.executor :as executor]))

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
  "A run function designed to be called from an applications -main fn.

  Accepts executor params (see below) as well as process argv arguments.

  Available arguments are `[up, down, next, undo, redo]`.

  Executor `props` should be provided containing:

  - :context - context map to be passed to executing operations
  - :store - a DataStore implementation
  - :operations - a set of operations to be executed"
  [props args]
  (condp = (keyword (first args))
    :up (run-up! props)
    :down (run-down! props)
    :next (run-next! props)
    :undo (undo! props)
    :redo (redo! props)))
