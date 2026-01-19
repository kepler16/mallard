(ns hooks.k16.mallard.loader.ns
  (:require
   [clj-kondo.hooks-api :as api]))

(defn load! [{:keys [node]}]
  (let [[_ opts namespaces] (:children node)

        namespaces (if-not namespaces
                     opts
                     namespaces)]
    (when-not (= :vector (api/tag namespaces))
      (api/reg-finding! (assoc (meta namespaces)
                               :message "load! should be provided a vector of namespaces"
                               :type :mallard/invalid-usage)))

    (doseq [namespace (:children namespaces)]
      (when-not (symbol? (api/sexpr namespace))
        (api/reg-finding! (assoc (meta namespace)
                                 :message "all namespace elements given to load! must be symbols"
                                 :type :mallard/invalid-usage))))

    (let [new-node (api/list-node
                    (into [(api/token-node 'do)]
                          (mapv
                           (fn [node]
                             (api/list-node
                              [(api/token-node 'require)
                               (api/list-node [(api/token-node 'quote)
                                               node])]))
                           (:children namespaces))))]

      {:node new-node})))
