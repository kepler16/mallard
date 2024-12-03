(ns build
  (:require
   [clojure.tools.build.api :as b]
   [k16.kmono.version :as kmono.version]
   [k16.kaven.deploy :as kaven.deploy]
   [k16.kmono.build :as kmono.build]
   [k16.kmono.core.config :as core.config]
   [k16.kmono.core.fs :as core.fs]
   [k16.kmono.core.graph :as core.graph]
   [k16.kmono.core.packages :as core.packages]
   [k16.kmono.git.tags :as git.tags]))

(defn- get-latest-version [dir]
  (->> (git.tags/get-sorted-tags dir)
       (map (fn [tag]
              (second (re-matches #"v(.*)" tag))))
       (remove nil?)
       first))

(defn- load-packages []
  (let [project-root (core.fs/find-project-root!)
        workspace-config (core.config/resolve-workspace-config project-root)
        packages (core.packages/resolve-packages project-root workspace-config)
        version (get-latest-version project-root)]
    (reduce
     (fn [packages [pkg-name pkg]]
       (assoc packages pkg-name (update pkg :version #(or version %))))
     {}
     (kmono.version/resolve-package-versions project-root packages))))

(def class-dir "target/classes")
(def jar-file "target/lib.jar")

(defn build [_]
  (b/delete {:path "target"})

  (let [packages (load-packages)]
    (kmono.build/for-each-package packages
      (fn [pkg]
        (let [pkg-name (:fqn pkg)
              basis (kmono.build/create-basis packages pkg)]

          (b/copy-dir {:src-dirs (:paths basis)
                       :target-dir class-dir})

          (b/write-pom {:class-dir class-dir
                        :lib pkg-name
                        :version (:version pkg)
                        :basis basis
                        :src-dirs (:paths basis)
                        :pom-data [[:description "Clojure migrations API"]
                                   [:url "https://github.com/kepler16/mallard"]
                                   [:licenses
                                    [:license
                                     [:name "MIT"]
                                     [:url "https://opensource.org/license/mit"]]]]})

          (b/jar {:class-dir class-dir
                  :jar-file jar-file}))))))

(def ^:private clojars-credentials
  {:username (System/getenv "CLOJARS_USERNAME")
   :password (System/getenv "CLOJARS_PASSWORD")})

(defn release [_]
  (let [packages (core.graph/filter-by kmono.build/not-published? (load-packages))]
    (kmono.build/for-each-package packages
      (fn [_]
        (kaven.deploy/deploy
         {:jar-path (b/resolve-path jar-file)
          :repository {:id "clojars"
                       :credentials clojars-credentials}})))))
