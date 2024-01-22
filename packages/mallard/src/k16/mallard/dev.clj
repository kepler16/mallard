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
     [:migrations executor/?Migrations]]

    [:map
     [:migrations-dir :string]]]])

(def run-migrations-component!
  {:gx/start
   {:gx/processor (fn [{:keys [props]}]
                    (let [{:keys [store context migrations migrations-dir]} props]
                      (api/run-up! {:store store
                                    :context context
                                    :migrations (or migrations
                                                    (loaders.fs/load-migrations! migrations-dir))})))
    :gx/props-schema ?Props}})
