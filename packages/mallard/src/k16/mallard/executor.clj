(ns k16.mallard.executor
  (:require
   [k16.mallard.datastore :as datastore.api]
   [malli.core :as m]
   [malli.error :as me]
   [tick.core :as t]
   [taoensso.timbre :as log]))

(set! *warn-on-reflection* true)

(def ?Migration
  [:map
   [:id :string]
   [:run-up! {:error/message "should be a function with one argument"
              :optional true}
    [:=> [:cat :any] :any]]
   [:run-down! {:error/message "should be a function with one argument"
                :optional true}
    [:=> [:cat :any] :any]]])

(def ?Migrations
  [:sequential {:error/message "should be a sequence of migrations"}
   ?Migration])

(def ?ExecuteProps
  [:map
   [:context {:optional true} [:maybe :any]]
   [:store datastore.api/?DataStore]
   [:migrations ?Migrations]
   [:limit {:optional true} [:int {:min 1}]]
   [:direction [:enum :up :down]]])

(defn- project-op-log
  "Reduces over the op-log in order to determine what migration id is currently
   applied. Returns the last applied migration id."
  [op-log]
  (->> op-log
       (reduce
        (fn [ids op]
          (case (:direction op)
            :up (conj ids (:id op))
            :down (if (= (:id op) (last ids))
                    (pop ids)
                    (throw (ex-info "Error reprocessing op-log. A :down operation did not
                                     follow an :up migration of the same id"
                                    {:last-op (last ids)
                                     :current-op (:id op)})))))
        [])
       last))

(defn- index-of [item coll]
  (let [index
        (->> coll
             (map-indexed vector)
             (some (fn [[idx val]]
                     (when (= val item) idx))))]
    (if index index -1)))

(defn- get-index
  "Determines the index of the currently applied index in the given collection of migrations"
  [op-log migrations]
  (if op-log
    (let [last-run-id (project-op-log op-log)
          index (->> migrations
                     (map :id)
                     (index-of last-run-id))]

      (if (and (not (nil? last-run-id)) (= index -1))
        (throw (ex-info (str "The last run migration " last-run-id " was not found in the given set of migrations") {}))
        index))

    -1))

(defn- execute-one!
  "Execute a single migration and return an ?Operation to be appended to the op-log."
  [context migration direction]
  (let [{:keys [id run-up! run-down!]} migration
        ts (t/now)]
    (log/info (str "Executing migration " id " [" direction "]"))

    (case direction
      :up (run-up! context)
      :down (run-down! context))

    (log/info "Success")

    {:id id
     :direction direction
     :started_at ts
     :finished_at (t/now)}))

(defn- find-unapplied
  "Return an ordered set of unapplied migrations based on the current op-log state. This will
   return migrations in reverse order if the direction is :down"
  [op-log migrations direction]
  (let [migrations' (if (= direction :down) (reverse migrations) migrations)
        index (get-index op-log migrations')
        ;; An :up migration needs to start at the next migration whereas a :down migration
        ;; should start at the currently applied migration.
        index' (if (= :up direction) (inc index) index)]
    (subvec (vec migrations') index')))

(defn execute!
  "Execute the given migrations and return the new log of operations. This will handle locking
   and will mutate the datastore with the changing op-log as migrations are applied."
  [{:keys [context store migrations direction limit] :as props}]
  (when-not (m/validate ?ExecuteProps props)
    (throw (ex-info "Migration props are invalid"
                    {:errors (me/humanize (m/explain ?ExecuteProps props))})))

  (let [state (datastore.api/load-state store)
        op-log (atom (or (:log state) []))
        unapplied (find-unapplied (:log state) migrations direction)
        unapplied' (if limit (take limit unapplied) unapplied)
        lock (datastore.api/acquire-lock! store)]

    (try
      (if (pos? (count unapplied'))
        (log/info (str "Running " (count unapplied') " operations"))
        (log/info "No unapplied operations to run"))

      (doseq [migration unapplied']
        (let [op (execute-one! context migration direction)
              op-log' (swap! op-log #(conj % op))]
          (datastore.api/save-state! store {:log op-log'})))

      (catch Exception e
        (log/error "Failed to execute operation" e)
        (throw e))
      (finally (datastore.api/release-lock! store lock)))

    @op-log))
