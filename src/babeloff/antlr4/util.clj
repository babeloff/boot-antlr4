(ns babeloff.antlr4.util
  "an antlr task that builds lexers and parsers."
  (:import (org.antlr.v4 Tool)
           (org.antlr.v4.tool Grammar)
           (org.antlr.v4.runtime RuleContext
                                 CharStream CharStreams
                                 CommonToken CommonTokenStream
                                 Lexer Parser
                                 Token TokenStream)
           (org.antlr.v4.runtime.atn ATN)
           (org.antlr.v4.runtime.tree Trees)))


;; https://github.com/antlr/antlr4/blob/master/tool/src/org/antlr/v4/Tool.java
(defn run-tool!
  "Run the antlr grammar interpreter"
  [args]
  (let [antlr (Tool. (into-array args))]
    (.processGrammarsOnCommandLine antlr)))
