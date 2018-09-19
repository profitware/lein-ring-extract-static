(ns leiningen.ring-extract-static
  (:use [clojure.java.io :as io]
        [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.server :refer :all]
        [leiningen.ring.util :only (ensure-handler-set! update-project ring-version)]
        [ring-extract-static.common :as common]))


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
       `(let [public-dir# ~(common/get-public-dir project)
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
                ~(common/get-docker-data project :contents))
          (spit (io/file public-dir# ".dockerignore")
                ~(common/get-docker-data project :dockerignore-contents))
          (doall (map (fn [[uri# resource-file#]]
                        (when (string? uri#)
                          (spit (io/file public-dir# resource-file#)
                                (:body (apply handler#
                                              (get-options# uri#))))))
                      static-map#)))
       (apply load-namespaces
              (conj (into
                     ['ring.server.leiningen
                      (if (nrepl? project) 'clojure.tools.nrepl.server)]
                     (if (nrepl? project) (nrepl-middleware project)))
                    (-> project :ring :handler)
                    (-> project :ring :init)
                    (-> project :ring :destroy)))))))
