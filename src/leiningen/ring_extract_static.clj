(ns leiningen.ring-extract-static
  (:use [clojure.java.io :as io]
        [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.server :refer :all]
        [leiningen.ring.util :only (ensure-handler-set! update-project ring-version)]))


(defn get-docker-contents [project]
  (str "FROM " (if (:openshift project)
                 "twalter/openshift-nginx:mainline-alpine"
                 "nginx:alpine")
       \newline
       "COPY . /usr/share/nginx/html" \newline))


(def ^:dynamic *resource-name* "public")


(defn ring-extract-static
  "Generate static from ring endpoints."
  [project]
  (let [options {}]
    (ensure-handler-set! project)
    (let [project (-> project
                      (assoc-in [:ring :reload-paths] (reload-paths project))
                      (update-in [:ring] merge options))]
      (eval-in-project
       (-> project add-server-dep add-optional-nrepl-dep)
       `(let [public-dir# (-> (Thread/currentThread)
                              .getContextClassLoader
                              (.getResources ~*resource-name*)
                              enumeration-seq
                              first)
              handler# ~(-> project
                           :ring
                           :handler)
              port# (or ~(-> project
                             :ring
                             :port)
                        3000)
              static-map# ~(-> project
                               :ring
                               :static)
              get-options# (fn [uri#]
                             [{:server-port port#
                               :server-name "localhost"
                               :remote-addr "127.0.0.1"
                               :uri uri#
                               :scheme "http"
                               :request-method :get}])]
          (spit (io/file public-dir# "Dockerfile")
                ~(get-docker-contents project))
          (doall (map (fn [[uri# resource-file#]]
                        (spit (io/file public-dir# resource-file#)
                              (:body (apply handler#
                                            (get-options# uri#)))))
                      static-map#)))
       (apply load-namespaces
              (conj (into
                     ['ring.server.leiningen
                      (if (nrepl? project) 'clojure.tools.nrepl.server)]
                     (if (nrepl? project) (nrepl-middleware project)))
                    (-> project :ring :handler)
                    (-> project :ring :init)
                    (-> project :ring :destroy)))))))
