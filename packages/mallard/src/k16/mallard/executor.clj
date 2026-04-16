(ns k16.mallard.executor
  (:require
   [k16.mallard.store :as datastore.api]
   [k16.mallard.log :as log]
   [k16.mallard.logger :as logger]
   [malli.core :as m]
   [malli.error :as me]))

(def ?Operation
  [:map {:closed true}
   [:id :string]
   [:metadata {:optional true} :map]
   [:run-up! {:error/message "should be a function with one argument"
              :optional true}
    [:=> [:cat :any] :any]]
   [:run-down! {:error/message "should be a function with one argument"
                :optional true}
    [:=> [:cat :any] :any]]])

(def ?Operations
  [:sequential {:error/message "should be a sequence of operations"}
   ?Operation])

(def ?ExecuteProps
  [:map {:closed true}
   [:context {:optional true} [:maybe :any]]
   [:store datastore.api/?DataStore]
   [:operations ?Operations]
   [:limit {:optional true} [:int {:min 1}]]
   [:direction [:enum :up :down]]])

(defn- execute-one!
  "Execute a single operation and return a [k16.mallard.store/?OpLogEntry] to be
   appended to the oplog."
  [context operation direction]
  (let [{:keys [id run-up! run-down! metadata]} operation
        ts (java.time.Instant/now)]
    (logger/info (str "Executing operation " id " [" direction "]"))

    (case direction
      :up (when run-up! (run-up! context))
      :down (when run-down! (run-down! context)))

    (logger/info "Success")

    (cond-> {:id id
             :direction direction
             :started_at ts
             :finished_at (java.time.Instant/now)}
      (seq metadata) (assoc :metadata metadata))))

(defn execute!
  "Execute the given operations and append to the oplog.

   This will handle locking and will mutate the datastore with the changing
   oplog as operations are applied.

   Returns the full oplog on completion."
  [{:keys [context store operations direction limit] :as props}]
  (when-not (m/validate ?ExecuteProps props)
    (throw (ex-info "Invalid arguments provided"
                    {:errors (me/humanize (m/explain ?ExecuteProps props))})))

  (let [state (datastore.api/load-state store)
        oplog (atom (or (:log state) []))
        unapplied (log/find-unapplied (:log state) operations direction)
        unapplied (if limit
                    (into []
                          (take limit)
                          unapplied)
                    unapplied)
        lock (datastore.api/acquire-lock! store)]

    (try
      (if (pos? (count unapplied))
        (logger/info (str "Running " (count unapplied) " operations [" direction "]"))
        (logger/info "No unapplied operations to run"))

      (doseq [{:keys [id operation]} unapplied]
        (when (not operation)
          (logger/error (str "Cannot run :down. Operation " id " is missing"))
          (throw (ex-info (str "Missing operation " id) {:operation-id id})))

        (let [op (execute-one! context operation direction)
              oplog' (swap! oplog #(conj % op))]
          (datastore.api/save-state! store {:log oplog'})))

      (catch Exception e
        (logger/error "Failed to execute operation" e)
        (throw e))
      (finally
        (datastore.api/release-lock! store lock)))

    @oplog))
