(ns k16.mallard.dev
  "For use in development, this is a component that can be included in a gx configuration to automatically
  run any migrations on system start."
  (:require
   [k16.mallard.api :as api]
   [k16.mallard.datastore :as datastore.api]
   [k16.mallard.loaders.fs :as loaders.fs]
   [k16.mallard.executor :as executor]))

(set! *warn-on-reflection* true)

(def ?Props
  [:and
   [:map
    [:store datastore.api/?DataStore]
    [:context :any]]

   [:or
    [:map
     [:operations executor/?Operations]]

    [:map
     [:load-dir :string]]]])

(def run!
  {:gx/start
   {:gx/processor (fn [{:keys [props]}]
                    (let [{:keys [store context operations load-dir]} props]
                      (api/run-up! {:store store
                                    :context context
                                    :operations (or operations
                                                    (loaders.fs/load! load-dir))})))
    :gx/props-schema ?Props}})
