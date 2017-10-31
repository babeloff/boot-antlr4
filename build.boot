(def project 'babeloff/boot-antlr4)
(def version "2017.10.31")

(set-env! :resource-paths #{"resources" "src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE"]
                            [org.clojure/spec.alpha "0.1.143"]
                            [boot/core "RELEASE" :scope "test"]
                            [adzerk/boot-test "RELEASE" :scope "test"]
                            ;; https://mvnrepository.com/artifact/org.antlr/antlr4
                            [org.antlr/antlr4 "4.7"]
                            [rdf-clj "0.2.0-SNAPSHOT"]
                            [org.clojure/java.classpath "0.2.3"]
                            [adzerk/bootlaces "0.1.13" :scope "test"]])

(require '[adzerk.boot-test :refer [test]]
  '[adzerk.bootlaces :refer [bootlaces!
                              build-jar
                              push-snapshot
                              push-release]])
(require '[rdf :as rdf])
(bootlaces! version)

(task-options!
 pom {:project     project
      :version     version
      :description "boot tasks for working with ANTLR4"
      :url         "https://github.com/babeloff/boot-antlr4/wiki"
      :scm         {:url "https://github.com/babeloff/antlr4"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (build-jar) (install)))

(deftask release
  "release to clojars
  You will be prompted for
  your clojars user and password."
  []
  (comp
    (build-jar)
    (push-release)))
