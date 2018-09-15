(ns leiningen.ring-build-static
  (:require [clojure.java.shell :as shell]
            [ring-extract-static.common :as common]))


(defn- ring-only-build-static [project]
  (let [image-name (common/get-docker-data project :image-name)]
    (shell/with-sh-dir (common/get-public-dir project)
      (common/exec-docker project "build" "-t" (str image-name ":latest") "."))))


(defn- ring-build-and-run-static [project]
  (let [image-name (common/get-docker-data project :image-name)
        exposed-port (common/get-docker-data project :exposed-port)]
    (shell/with-sh-dir (common/get-public-dir project)
      (ring-only-build-static project)
      (common/exec-docker project "run" "-p" exposed-port (str image-name ":latest")))))


(defn- ring-build-and-push-static [project]
  (let [image-name (common/get-docker-data project :image-name)]
    (shell/with-sh-dir (common/get-public-dir project)
      (when-let [openshift-config (:openshift project)]
        (let [openshift-registry (common/get-openshift-registry openshift-config)]
          (common/exec-docker project "login" "-u" "`oc whoami`" "-p" "`oc whoami -t`" openshift-registry)))
      (ring-only-build-static project)
      (common/exec-docker project "push" (str image-name ":latest")))))


(defn ring-build-static
  "Build Docker using extracted ring static."
  [project & [command]]
  (let [x (case command
            "run" (ring-build-and-run-static project)
            "push" (ring-build-and-push-static project)
            (ring-only-build-static project))]
    x))
