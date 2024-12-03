# Mallard

This is a Clojure migrations API which aims to provide a generalized and versatile mechanism for running arbitrary
migration operations.

[![Clojars Project](https://img.shields.io/clojars/v/com.kepler16/mallard.svg)](https://clojars.org/com.kepler16/mallard)

## Goals

- Allow running arbitrary operations as code.
- Assume nothing about the work being performed.
- Allow embedding migrations and the executor directly into an application.
- Allow storing migration state anywhere including in a store separate to data being operated on.
- Have a mechanism for locking to prevent multiple operations from occurring at the same time.
- Keep a log of all operations that have ever been executed, including rollbacks, for historical records
- Allow deleting operations after they have been applied.

## Quick start

The expectation here is for this to be embedded into your application and therefore the setup is to configure a
namespace with a `-main` function that calls the mallard APIs.

1. Add mallard to your project

```clojure
;; deps.edn
{:deps {com.kepler16/mallard {:mvn/version "LATEST"}}}
```

2. Add a `migrate` namespace

```clojure
(ns example.main
  (:require
   [k16.mallard.store.memory :as store.memory]
   [k16.mallard :as mallard]))

(def ^:private migrations
  (loader.fs/load! "example/migrations"))

(defn run-migrations [args]
  (let [datastore (store.memory/create-datastore)]
    (mallard/run {:context {}
                  :store datastore
                  :operations migrations}
                 args)))

(defn -main [& args]
  (run-migrations args))
```

3. Add a migration file containing a `run-up!` and an optional `run-down!` function.

```clj
(ns example.migrations.init-1)

(defn run-up! [context]
  (println "running up!"))

(defn run-down! [context]
  (println "running down!"))
```

4) And execute your migrations:

```bash
# Run all up migrations
clojure -M -m example.migrate up
# Undo all migrations. Executes in reverse order
clojure -M -m example.migrate down
# Rerun the last applied migration (runs down then up)
clojure -M -m example.migrate redo
# Undo the last applied migration
clojure -M -m example.migrate undo
# Run the next unapplied migration. Same as up, but runs only 1
clojure -M -m example.migrate next
```

See the **[example project](./example/)** for a more involved setup.

## How it works

Operations are tracked using an op-log which is an append-only log of all operations that have been executed. An
operation in the op-log has a unique `id`, a `direction` (up or down) and timestamp information regarding when it ran.

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

Given the op-log and a set of ordered, uniquely identifiable migrations we can determine the subset of migrations which
need to still be applied.

```clj
(def migrations [{:id "1"} {:id "2"} {:id "3"} {:id "4"}])

(find-unapplied op-log migrations :up) ;; => [{:id "3"} {:id "4"}]
(find-unapplied op-log migrations :down) ;; => [{:id "2"} {:id "1"}]
```

The executor simply finds all unapplied migrations for the desired 'direction' and applies them in order, updating the
op-log as it completes.

## Compression / Clean-up

Migrations are generally once-off operations applied at a point in time, while your application itself changes through
time. This makes keeping old migration code around often infeasible as it will often be referencing code in your
application which has changed or been removed, or it is applying to data structures which have completely changed or
also been removed.

Some migrations scripts, such as index creation/modification, we _do_ want to keep around as they are useful to apply
when setting up a database (either during development on a new machine, or when your application is often deployed to
fresh infrastructure). However these kinds of migrations we might want to 'compress' into a single 'init' or 'seed'
migration. For example, when indexes are added/removed/changed this would need to happen in a new migration - but once
this has been applied everywhere it might make sense to 'merge' the code back into an original 'init' migration.

Both types of cleanup are indirectly supported by this executor. Any previously applied operations can be deleted and
the executor should be able to figure out from where to continue. The only thing to be careful with is renaming
operations - **do not rename an operation if you do not want it to be applied again**.

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

## GraalVM Native-Image Compatibility

This tool is fully compatible with graalvm native-image. To properly structure your project you need to make sure your
migrations are analysed at build time.

You can either explicitly require each migration and use the `ns` loader or you can use the `fs` loader from a def:

```clj
(def migrations
  "Preload migrations to ensure that the `require` statements are analysed during native-image compilation"
  (loaders.fs/load-migrations! "migrations")) ;; where the folder `migrations` is on your classpath.
```

## Roadmap

- Provide some way to run a migration on the migration state.
