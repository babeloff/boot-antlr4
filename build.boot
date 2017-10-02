(def project 'babeloff/boot-antlr4)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"resources" "src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE"]
                            [boot/core "RELEASE" :scope "test"]
                            [adzerk/boot-test "RELEASE" :scope "test"]
                            ;; https://mvnrepository.com/artifact/org.antlr/antlr4
                            [org.antlr/antlr4 "4.7"]])

(task-options!
 pom {:project     project
      :version     version
      :description "FIXME: write description"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/babeloff/antlr4"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})



(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))

(require '[adzerk.boot-test :refer [test]]
         '[babeloff.boot-antlr4 :refer [antlr4
                                        antlr4-interpreter]])
