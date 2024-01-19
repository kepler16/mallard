(ns k16.mallard.dev
  "For use in development, this is a component that can be included in a gx configuration to automatically
  run any migrations on system start."
  (:require
   [k16.mallard.api :as api]
   [k16.mallard.datastore :as datastore.api]
   [k16.mallard.loaders.fs :as loaders.fs]))

(set! *warn-on-reflection* true)

(def ?Props
  [:map
   [:migrations-dir :string]
   [:store datastore.api/?DataStore]
   [:context :any]])

(def run-migrations-component!
  {:gx/start
   {:gx/processor (fn [{:keys [props]}]
                    (let [{:keys [store context migrations-dir]} props]
                      (api/run-up! {:store store
                                    :context context
                                    :migrations (loaders.fs/load-migrations! migrations-dir)})))
    :gx/props-schema ?Props}})
