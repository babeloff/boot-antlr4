(ns babeloff.boot-antlr4
  "an antlr task that builds lexers and parsers."
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.util :as util]
            [clojure.pprint :as pp])
  (:import (org.antlr.v4 Tool)
           (org.antlr.v4.gui TestRig)
           (org.antlr.v4.tool LexerGrammar
                              Grammar)
           (org.antlr.v4.parse ANTLRParser)
           (org.antlr.v4.runtime CharStreams
                                 CommonTokenStream
                                 Lexer LexerInterpreter
                                 Parser ParserInterpreter)
           (org.antlr.v4.runtime.tree ParseTree)
           (java.nio.file Paths)))


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

(defn value-opt!
  [args option object]
  (if object
    (do
      ;(util/info "arg: %s %s\n" option object)
      (conj! args option object))
    (do
      ;(util/info "no-arg: %s %s\n" option object)
      args)))


(defn bool-opt!
  [args option object]
  (if (sequential? option)
    (do
      ;(util/info "arg: %s %s\n" option object)
      (conj! args (if object (first option) (second option))))
    (do
      ;(util/info "arg: %s %s\n" option object)
      (if object
        (conj! args option)
        args))))

(defn override-opt!
  [args overrides]
  (if-not (empty? overrides)
    (do)
      ;(util/info "no-overrides\n"))
    (loop [argset args,
           items overrides]
      (if (empty? items)
        argset
        (do
          ;(util/info "arg: %s\n" (first items))
          (recur (conj! argset (str "-D" (first items)))
                 (rest items)))))))

(defn source-opt!
  [args source fileset]
  (if source
    (conj! args "-lib" source)
    (-> args
      (conj! "-lib")
      (conj! (.getCanonicalPath (first (boot/input-dirs fileset)))))))

;; https://github.com/antlr/antlr4/blob/master/doc/tool-options.md
;; https://github.com/antlr/antlr4/blob/master/tool/src/org/antlr/v4/Tool.java
(deftask antlr4
  "A task that generates parsers and lexers from antlr grammars."
  [g grammar        FILE    str    "grammar file"
   o target         OUT_DIR str    "specify output directory where all output is generated"
   l source         LIB_DIR str    "specify location of grammars, tokens files"
   e encoding       CODE    str    "specify grammar file encoding; e.g., euc-jp"
   f message-format STYLE   str    "specify output STYLE for messages in antlr, gnu, vs2005"
   p package        NAME    str    "specify a package/namespace for the generated code"
   D override       OPT     [str]  "<option>=value  set/override a grammar-level option"
   s show                   bool   "show the constructed properties"
   a atn                    bool   "generate rule augmented transition network diagrams"
   v long-messages          bool   "show exception details when available for errors and warnings"
   _ listener               bool   "generate parse tree listener"
   _ visitor                bool   "generate parse tree visitor"
   d depend                 bool   "generate file dependencies"
   w warn-error             bool   "treat warnings as errors"
   _ save-lexer             bool   "extract lexer from combined grammar"
   _ debug-st               bool   "launch StringTemplate visualizer on generated code"
   _ debug-st-wait          bool   "wait for STViz to close before continuing"
   _ force-atn              bool   "use the ATN simulator for all predictions"
   _ log                    bool   "dump lots of logging info to antlr-timestamp.log"]
  (boot/with-pre-wrap fileset
    (if-not grammar
      (do
        (boot.util/fail "The --grammar argument is required")
        (*usage*)
        fileset)
      (do
        ;(util/info "grammar: %s\n" grammar)
        ;(util/info "source: %s\n" source)
        ;(util/info "target: %s\n" target)
        (let [target-dir (boot/tmp-dir!)
              target-dir-str (.getCanonicalPath target-dir)
              args
              (-> (transient ["-o" target-dir-str])
                  (source-opt! source fileset)
                  (value-opt! "-encoding" encoding)
                  (value-opt! "-message-format" message-format)
                  (value-opt! "-package" package)

                  (override-opt! override)

                  (bool-opt! "-atn" atn)
                  (bool-opt! "-long-messages" long-messages)
                  (bool-opt! ["-listener" "-no-listener"] listener)
                  (bool-opt! ["-visitor" "-no-visitor"] visitor)
                  (bool-opt! "-depend" depend)

                  (bool-opt! "-Werror" warn-error)
                  (bool-opt! "-XdbgST" debug-st)
                  (bool-opt! "-Xsave-lexer" save-lexer)
                  (bool-opt! "-XdbgSTWait" debug-st-wait)
                  (bool-opt! "-Xforce-atn" force-atn)
                  (bool-opt! "-Xlog" log)
                  (conj! grammar))
              args1 (persistent! args)]

            (if show
              (do
                (util/info "arguments: %s\n" *opts*)
                fileset)
              (do
                (util/info "compiling grammar: %s\n" args1)
                (Tool. (into-array args1))
                (-> fileset
                    (boot/add-resource target-dir)
                    boot/commit!))))))))

;;(deftesttask antlr4-tests []
;;  (comp (antlr4 :grammar "AqlCommentTest.g4" :show 5)
;;        (to-asset-invert-tests))))
