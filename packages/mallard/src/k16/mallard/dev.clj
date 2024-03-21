(ns k16.mallard.dev
  "For use in development, this is a component that can be included in a gx configuration to automatically
  run any migrations on system start."
  (:refer-clojure :exclude [run!])
  (:require
   [k16.mallard.api :as api]
   [k16.mallard.datastore :as datastore.api]
   [k16.mallard.executor :as executor]))

(set! *warn-on-reflection* true)

(def ?Props
  [:and
   [:map
    [:store datastore.api/?DataStore]
    [:context :any]]

   [:or
    [:map
     [:operations
      [:or executor/?Operations
       [:=> [:cat] executor/?Operations]]]]]])

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(def run!
  {:gx/start
   {:gx/processor (fn [{:keys [props]}]
                    (let [{:keys [store context operations]} props]
                      (api/run-up! {:store store
                                    :context context
                                    :operations (if (fn? operations)
                                                  (operations)
                                                  operations)})))
    :gx/props-schema ?Props}})
