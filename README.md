# Mallard

This is a Clojure migrations framework which aims to provide a generalized and versatile mechanism for running arbitrary migration operations. It has the following goals:

+ Allow running arbitrary migrations as code.
+ Allow storing migration state anywhere, including in a separate store to the data being migrated.
+ Handle locking to prevent multiple migration operations occurring at the same time.
+ Keep a log of all migration operations that have been ever been executed, including rollbacks, and record their date and timing information.
+ Allow 'compressing' migrations or cleaning up by removing previously applied migrations.

## Quick start

Add a new `migrate` alias to your projects `deps.edn` file.

```clj
;; deps.edn
{:aliases {:migrate {:extra-deps {transit-engineering/migrate.clj {:local/root "RELEASE"}}
                     :exec-fn transit.migrate.api/run
                     :exec-args {:migrations-dir "resources/migrations"
                                 :init-store! my.app.migrate.store/init!
                                 :action :up}}}}
```

Define your datastore. Note that in practice you should use a datastore that is backed by a persistent database.

```clj
(ns my.app.migrate.store
  (:require [transit.migrate.stores.memory :as datastore.memory]))

(defn init! []
  (datastore.memory/create-memory-datastore))
```

Add a new migration with a `run-up!` and a `run-down!` function defined.

```clj
(ns my.app.migrations.init-1)

(defn run-up! []
  (println "running up!"))

(defn run-down! []
  (println "running down!"))
```

And execute your migrations:

```bash
clojure -X:migrate # Run all up migrations

# Or you can provide the action explicitly.

clojure -X:migrate :action :up # The same as before, just more explicit
clojure -X:migrate :action :down # Undo all applied migrations
clojure -X:migrate :action :redo # Redo the last applied migration
clojure -X:migrate :action :undo # Undo the last applied migration
clojure -X:migrate :action :next # Run the next unapplied migration
```

## How it works

Migrations are tracked using an op-log which is an ever increasing log of migration operations that have been executed. An operation tracks a migration id, the 'direction' (up or down) and the datetime of when it ran.

It looks like this:

```clj
(def op-log
  [{:id "1"
    :direction :up
    :started_at #time/instant "2023-04-29T11:51:05.278832Z"
    :finished_at #time/instant "2023-04-29T11:51:05.279392Z"}
   {:id "2"
    :direction :up
    :started_at #time/instant "2023-04-29T11:51:05.287456Z"
    :finished_at #time/instant "2023-04-29T11:51:05.287506Z"}
   {:id "2"
    :direction :down
    :started_at #time/instant "2023-04-29T11:51:05.289393Z"
    :finished_at #time/instant "2023-04-29T11:51:05.289493Z"}
   {:id "2"
    :direction :up
    :started_at #time/instant "2023-04-29T11:51:05.290993Z"
    :finished_at #time/instant "2023-04-29T11:51:05.291087Z"}])
```

Given the op-log and a set of ordered, uniquely identifiable migrations we can determine the subset of migrations which need to still be applied.

```clj
(def migrations [{:id "1"} {:id "2"} {:id "3"} {:id "4"}])

(find-unapplied op-log migrations :up) ;; => [{:id "3"} {:id "4"}]
(find-unapplied op-log migrations :down) ;; => [{:id "2"} {:id "1"}]
```

The executor simply finds all unapplied migrations for the desired 'direction' and applies them in order, updating the op-log as it completes.

## Compression/Cleanup

Migrations are once-off operations applied at a point in time, while your application itself changes through time. This makes keeping old migration code around often infeasible as it will often be referencing code in your application which has changed or been removed, or it is applying to data structures which have completely changed or also been removed.

Some migrations scripts, such as index creation/modification, we _do_ want to keep around as they are useful to apply when setting up a database (either during development on a new machine, or when your application is often deployed to fresh infrastructure). However these kinds of migrations we might want to 'compress' into a single 'init' or 'seed' migration. For example, when indexes are added/removed/changed this would need to happen in a new migration - but once this has been applied everywhere it might make sense to 'merge' the code back into an original 'init' migration.

Both types of cleanup are indirectly supported by this executor so long as you keep in mind how it operates. The executor always needs the last applied migration (as derived from the op-log) to be present in the provided set of migrations. This is what is used as a reference point for where it should resume. So long as this referential migration is not dropped, any migrations before or after can be changed as you wish.

As an example the following state would execute just fine:

```clj
(def op-log
  [{:id "1"
    :direction :up}
   {:id "2"
    :direction :up}
   {:id "3"
    :direction :up}])

(def migrations [{:id "1"} {:id "3"} {:id "4"}])

(find-unapplied op-log migrations :up) ;; => [{:id "4"}]
(find-unapplied op-log migrations :down) ;; => [{:id "1"}]
```

Notice the migration with id `"2"` is missing from the provided set, simulating it having been cleaned up. Once the op-log has moved on to `"4"`, the migration with id `"3"` could also theoretically be removed.
