(ns babeloff.antlr4.interpret
    "antlr functions for lexers and parsers."
    {:boot/export-tasks true}
    (:require (boot [core :as boot :refer [deftask]]
                    [util :as util]
                    [task-helpers :as helper])
              (clojure [pprint :as pp])
              (clojure.java [io :as io]
                            [classpath :as cp]))
    (:import (clojure.lang DynamicClassLoader
                           Reflector)
             (org.antlr.v4 Tool)
             (org.antlr.v4.tool LexerGrammar
                                Grammar)
             (org.antlr.v4.parse ANTLRParser)
             (org.antlr.v4.runtime CharStream CharStreams
                                   CommonToken CommonTokenStream
                                   DiagnosticErrorListener
                                   Lexer LexerInterpreter
                                   Parser ParserInterpreter
                                   ParserRuleContext
                                   Token TokenStream)
             (org.antlr.v4.runtime.atn PredictionMode)
             (org.antlr.v4.runtime.tree ParseTree)
             (org.antlr.v4.gui Trees)
             (java.nio.file Paths)
             (java.nio.charset Charset)
             (java.io IOException)
             (java.lang.reflect Constructor
                                InvocationTargetException
                                Method)
             (java.util ArrayList
                        List)))
  
;; https://github.com/antlr/antlr4/blob/master/doc/interpreters.md

(defn load-lexer-grammar
    [^String lexerGrammarFileName]
    ^LexerGrammar (Grammar/load lexerGrammarFileName))
  
(defn load-parser-grammar
  [^LexerGrammar lexer-grammar
   ^String parserGrammarFileName]
  ^ParserGrammar (Grammar/load parserGrammarFileName))
  
  
(defn interpret-combined
  [^String fileName
   ^String combinedGrammarFileName
   ^String startRule]
  (let [grammar (Grammar/load combinedGrammarFileName)
        char-stream (CharStreams/fromPath (Paths/get fileName))
        lexer (.createLexerInterpreter grammar char-stream)
  
        token-stream (CommonTokenStream. lexer)
        parser (.createParserInterpreter grammar token-stream)]
  
    (.parse parser (.index (.getRule grammar startRule)))))
  
(defn interpret-separate
  [^String fileNameToParse
   ^String lexerGrammarFileName
   ^String parserGrammarFileName
   ^String startRule]
  (let [^LexerGrammar lexer-grammar (Grammar/load lexerGrammarFileName)
        char-stream (CharStreams/fromPath (Paths/get fileNameToParse))
        lexer (.createLexerInterpreter lexer-grammar char-stream)
  
        parser-grammar (Grammar/load parserGrammarFileName)
        token-stream  (CommonTokenStream. lexer)
        parser (.createParserInterpreter parser-grammar token-stream)]
  
    (.parse parser (.index (.getRule parser-grammar startRule)))))
  
(defn print-tree [tree parser]
    (pp/pprint (.toStringTree tree parser)))
  
(deftask antlr4-interpreter
  "A task that returns a parser for a grammar.
     typically this parser would be returned to a REPL."
  [g grammar FILE str "grammar file"
   l lexer bool "enable lexer for grammar"
   p parser bool "enable parser for grammar"])
  