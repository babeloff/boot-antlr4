(def project 'babeloff/boot-antlr4)
(def version "2017.10.20-SNAPSHOT")

(set-env! :resource-paths #{"resources" "src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE"]
                            [org.clojure/spec.alpha "0.1.134"]
                            [boot/core "RELEASE" :scope "test"]
                            [adzerk/boot-test "RELEASE" :scope "test"]
                            ;; https://mvnrepository.com/artifact/org.antlr/antlr4
                            [org.antlr/antlr4 "4.7"]
                            [org.clojure/java.classpath "0.2.3"]
                            [adzerk/bootlaces "0.1.13" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "boot tasks for working with ANTLR4"
      :url         "https://github.com/babeloff/boot-antlr4/wiki"
      :scm         {:url "https://github.com/babeloff/antlr4"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})


(require '[adzerk.boot-test :refer [test]]
         '[adzerk.bootlaces :refer [bootlaces!
                                    build-jar
                                    push-snapshot
                                    push-release]])
(bootlaces! version)


(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))

(deftask release
  "release to clojars
  You will be prompted for
  your clojars user and password."
  []
  (comp
    (build-jar)
    (push-release)))
