
(def project 'babeloff/literate-antlr)
(def version "0.1.0-SNAPSHOT")

(set-env!
    :source-paths #{"src/antlr4" "src/java"}
    :dependencies '[[org.clojure/clojure "1.9.0-beta2"]
                    [boot/core "RELEASE" :scope "test"]
                    [babeloff/boot-antlr4 "0.1.0-SNAPSHOT"]
                    [org.antlr/antlr4 "4.7"]
                    [clj-jgit "0.8.10"]])

(task-options!
 pom {:project     project
      :version     version
      :packaging   "jar"
      :description "help in generating the java based parser for literate antlr"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/CategoricalData/fql"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}
      :developers  {"Fred Eisele" ""}})


;; (import '(org.antlr.v4.gui TestRig))
(require '[babeloff.boot-antlr4 :as antlr :refer [antlr4 test-rig]]
         '(boot [core :as boot :refer [deftask]]
                [util :as util]
                [task-helpers :as helper]))

(deftask store
  [s show bool "show the arguments"]
  (comp
    (target :dir #{"target"})))

(deftask build
  [s show bool "show the arguments"]
  (comp
    (antlr4 :grammar "ANTLRv4Lexer.g4"
            :package "org.antlr.parser.antlr4"
            :show true)
    (antlr4 :grammar "ANTLRv4Parser.g4"
            :package "org.antlr.parser.antlr4"
            :show true)
    (javac)))

(deftask exercise
  [s show bool "show the arguments"]
  (comp 
    (test-rig :parser "org.antlr.parser.antlr4.ANTLRv4Parser"
              :lexer "org.antlr.parser.antlr4.ANTLRv4Lexer"
              :start-rule "grammarSpec"
              :input ["src/antlr4/ANTLRv4Lexer.g4"
                      "src/antlr4/ANTLRv4Parser.g4"]
              :tree true
              :postscript true
              :tokens true
              :show true)))

(deftask my-repl
  []
  (comp (repl) (build) (store)))

(deftask live 
  []
  (comp ;(watch) 
    (build)
    (exercise) 
    ;; (show :fileset true) 
    (store)))
 
