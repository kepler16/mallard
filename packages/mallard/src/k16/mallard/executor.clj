(ns k16.mallard.executor
  (:require
   [k16.mallard.datastore :as datastore.api]
   [k16.mallard.log :as log]
   [malli.core :as m]
   [malli.error :as me]
   [tick.core :as t]))

(set! *warn-on-reflection* true)

(def ?Operation
  [:map
   [:id :string]
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
  [:map
   [:context {:optional true} [:maybe :any]]
   [:store datastore.api/?DataStore]
   [:operations ?Operations]
   [:limit {:optional true} [:int {:min 1}]]
   [:direction [:enum :up :down]]])

(defn- index-by [key-fn col]
  (->> col
       (map (fn [item]
              [(key-fn item) item]))
       (into {})))

(defn- project-op-log
  "Reduces over the op-log to project the concrete sequence of currently applied operation ids.
  
  The op-log contains a sequence of `:up` and `:down` operations which can be reduced down to a
  sequence of only `:up` operation ids
  
  Example:

  ```clojure
  (project-op-log [{:id \"1\" :direction :up}
                   {:id \"1\" :direction :down}
                   {:id \"2\" :direction :up}
                   {:id \"3\" :direction :up}])
  ;; => [\"2\" \"3\"]
  ```"
  [op-log]
  (reduce
   (fn [operations op]
     (case (:direction op)
       :up (conj operations (:id op))
       :down (if (= (:id op) (last operations))
               (pop operations)
               (throw (ex-info (str "Error reprocessing op-log. A :down operation did not "
                                    "follow an :up operation of the same id")
                               {:last-op (last operations)
                                :current-op (:id op)})))))
   []
   op-log))

(defn- derive-active-state
  "Determine what the current working state is based on the given `op-log` and set of ordered `operations`.

  Returns operations in two groups, those that have been applied and those that are yet to be applied.
  
  Operations in the `:applied` set may be ordered differently to how to are provided as the order they
  appear in `op-log` takes precedence.

  Operations in the `:applied` section maybe also be `nil` in the event that the operation that was applied
  as according to the `op-log` is no longer present or identifiable from the provided set of `operations`."
  [op-log operations]
  (let [operations-idx (index-by :id operations)
        applied-operation-ids (project-op-log op-log)

        applied-operations
        (mapv
         (fn [op-id]
           (let [operation (get operations-idx op-id)]
             {:id op-id
              :operation operation}))
         applied-operation-ids)

        applied-idx (index-by :id applied-operations)

        unapplied-operations
        (->> operations
             (filter
              (fn [operation]
                (not (get applied-idx (:id operation)))))
             (mapv (fn [operation]
                     {:id (:id operation)
                      :operation operation})))]

    {:applied applied-operations
     :unapplied unapplied-operations}))

(defn- find-unapplied
  "Return an ordered set of operations based on the current op-log state and desired `:direction`.
  
  - If the direction is `:up` this will return the remaining set of *unapplied* operations.
  - If the direction is `:down` this will return the *applied* operations in reverse order."
  [op-log operations direction]
  (let [{:keys [applied unapplied]} (derive-active-state op-log operations)]
    (case direction
      :up unapplied
      :down (reverse applied))))

(defn- execute-one!
  "Execute a single operation and return an ?OpLogEntry to be appended to the op-log."
  [context operation direction]
  (let [{:keys [id run-up! run-down!]} operation
        ts (t/now)]
    (log/info (str "Executing operation " id " [" direction "]"))

    (case direction
      :up (run-up! context)
      :down (run-down! context))

    (log/info "Success")

    {:id id
     :direction direction
     :started_at ts
     :finished_at (t/now)}))

(defn execute!
  "Execute the given operations and append to the op-log which is then returned. This will handle locking
   and will mutate the datastore with the changing op-log as operations are applied."
  [{:keys [context store operations direction limit] :as props}]
  (when-not (m/validate ?ExecuteProps props)
    (throw (ex-info "Invalid arguments provided"
                    {:errors (me/humanize (m/explain ?ExecuteProps props))})))

  (let [state (datastore.api/load-state store)
        op-log (atom (or (:log state) []))
        unapplied (cond-> (find-unapplied (:log state) operations direction)
                    limit ((partial take limit)))
        lock (datastore.api/acquire-lock! store)]

    (try
      (if (pos? (count unapplied))
        (log/info (str "Running " (count unapplied) " operations [" direction "]"))
        (log/info "No unapplied operations to run"))

      (doseq [{:keys [id operation]} unapplied]
        (when (not operation)
          (log/error (str "Cannot run :down. Operation " id " is missing"))
          (throw (ex-info (str "Missing operation " id) {:operation-id id})))

        (let [op (execute-one! context operation direction)
              op-log' (swap! op-log #(conj % op))]
          (datastore.api/save-state! store {:log op-log'})))

      (catch Exception e
        (log/error "Failed to execute operation" e)
        (throw e))
      (finally
        (datastore.api/release-lock! store lock)))

    @op-log))
