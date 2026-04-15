(ns k16.mallard.log)

(defn project
  "Reduces over the `oplog` to project the concrete sequence of currently
   applied operations.

   The `oplog` contains a sequence of `:up` and `:down` operations which can be
   reduced down to a sequence of only `:up` operation ids

   Example:

   ```clojure
   (project [{:id \"1\" :direction :up}
             {:id \"1\" :direction :down}
             {:id \"2\" :direction :up}
             {:id \"3\" :direction :up}])
   ;; => [{:id \"2\" ...} {:id \"3\" ...}]
   ```"
  [oplog]
  (reduce
   (fn [operations op]
     (case (:direction op)
       :up (conj operations (dissoc op :direction))
       :down (if (= (:id op) (:id (last operations)))
               (pop operations)
               (throw (ex-info (str "Error reprocessing oplog. A :down operation did not "
                                    "follow an :up operation of the same id")
                               {:last-op (last operations)
                                :current-op op})))))
   []
   oplog))

(defn- index-by [key-fn col]
  (into {}
        (map (juxt key-fn identity))
        col))

(defn reconcile-operations
  "Determine the working state by reconciling the given set of `operations`
   against the current `oplog`.

   Returns operations in two groups;

   - `:applied` - containing operations that have already been applied according
     to the log.
   - `:unapplied` - containing operations that have not yet been applied.

   Operations in the `:applied` set may be ordered differently to how they are
   provided as the order they appear in `oplog` takes precedence.

   Operations in the `:applied` section maybe also be `nil` in the event that
   the operation that was applied according to the `oplog` is no longer present
   or identifiable from the provided set of `operations`."
  [oplog operations]
  (let [operations-idx (index-by :id operations)
        applied-log (project oplog)

        applied-operations
        (mapv
         (fn [{:keys [id]}]
           (let [operation (get operations-idx id)]
             {:id id
              :operation operation}))
         applied-log)

        applied-idx (index-by :id applied-operations)

        unapplied-operations
        (into []
              (comp
               (filter
                (fn [operation]
                  (not (get applied-idx (:id operation)))))
               (map (fn [operation]
                      {:id (:id operation)
                       :operation operation})))
              operations)]

    {:applied applied-operations
     :unapplied unapplied-operations}))

(defn find-unapplied
  "Return an ordered set of unapplied operations, calculated from the current
   oplog state and desired `:direction`.

   - If the direction is `:up` this will return the remaining set of _unapplied_
     operations.
   - If the direction is `:down` this will return the set of _applied_
     operations in reverse order.

   When calculating operations in the `:down` direction, some entries may be
   returned as `nil` if no operation from the given set of `operations` could be
   found to match an entry in the oplog."
  [oplog operations direction]
  (let [{:keys [applied unapplied]} (reconcile-operations oplog
                                                          operations)]
    (case direction
      :up unapplied
      :down (reverse applied))))
