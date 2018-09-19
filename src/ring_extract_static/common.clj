(ns ring-extract-static.common
  (:use [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.server :refer :all]
        [leiningen.ring.util :only (ensure-handler-set! update-project ring-version)])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.classpath :as classpath]
            [cemerick.url :as url]))


(def ^:dynamic *default-resources-dir* "resources")
(def ^:dynamic *resource-name* "public")


(defn get-public-dir [project]
  (or (loop [[resource-path & rest-paths] (:resource-paths project)]
        (let [public-dir (try
                           (io/file resource-path *resource-name*)
                           (catch Exception _
                             nil))]
          (if (and public-dir
                   (.exists public-dir))
            (str public-dir)
            (when rest-paths
              (recur rest-paths)))))
      (let [public-dir (io/file *default-resources-dir*
                                *resource-name*)]
        (io/make-parents (io/file public-dir "Dockerfile"))
        (str public-dir))))


(defn exec-docker [project & args]
  (binding [eval/*dir* (get-public-dir project)]
    (apply main/warn "Exec: docker" args)
    (apply eval/sh "docker" args)))


(defn exec-oc [project & args]
  (binding [eval/*dir* (get-public-dir project)]
    (apply main/warn "Exec: oc" args)
    (apply eval/sh "oc" args)))


(defn get-openshift-registry [project]
  (let [config (:openshift project)]
    (when-let [api-server (or (:api-server config)
                              (try
                                (let [output (shell/sh "oc" "whoami" "--show-server")]
                                  (:host (url/url (:out output))))
                                (catch Exception _
                                  nil)))]
      (clojure.string/replace api-server
                              #"api"
                              "registry"))))


(defn get-docker-data [project data-type]
  (case data-type
    :contents (str "FROM " (if (:openshift project)
                             "twalter/openshift-nginx:mainline-alpine"
                             "nginx:alpine")
                   \newline
                   "COPY . /usr/share/nginx/html" \newline)
    :dockerignore-contents (str "Dockerfile" \newline
                                ".dockerignore" \newline)
    :exposed-port (if (:openshift project)
                    "8081:8081"
                    "80:80")
    :image-name (let [config (:openshift project)
                      app* (or (:static config)
                               (:app config)
                               (str (:name project)))
                      splitted-app (clojure.string/split app* #"/")
                      app (if-let [app-name (second splitted-app)]
                            app-name
                            app*)
                      namespace (or (when-let [namespace (:namespace config)]
                                      (str namespace))
                                    (if (= (first splitted-app) app)
                                      app
                                      (second splitted-app)))
                      openshift-registry (get-openshift-registry project)
                      registry (or (:registry config)
                                   openshift-registry)]
                  (or (get-in project [:ring :static :image-name])
                      (if openshift-registry
                        (str openshift-registry "/" namespace "/" app)
                        (if-let [docker-repo (get-in project [:docker :repo])]
                          docker-repo
                          (str namespace "/" app)))))))
