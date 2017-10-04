(ns babeloff.boot-antlr4
  "an antlr task that builds lexers and parsers."
  {:boot/export-tasks true}
  (:require [boot.core :as boot :refer [deftask]]
            [boot.util :as util]
            [clojure.java.io :as io]
            [boot.task-helpers :as helper]
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


(defmacro with-err-str
  "Evaluates exprs in a context in which *out* and *err*
  are bound to a fresh StringWriter.
  Returns the string created by any nested printing calls."
  {:added "1.9"}
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*out* s#, *err* s#]
       ~@body
       (str s#))))

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

(defn show-fileset
  [fileset]
  (util/info "show fileset\n")
  (doseq [tf (boot/input-files fileset)]
    (util/info "input %s\n" (boot/tmp-path tf)))
  (doseq [tf (boot/output-files fileset)]
    (util/info "output %s\n" (boot/tmp-path tf)))
  (doseq [tf (boot/user-files fileset)]
    (util/info "user %s\n" (boot/tmp-path tf)))
  (helper/print-fileset fileset)
  fileset)

(defn show-array
  [arry]
  (loop [arr arry, builder (StringBuilder.)]
    (if (empty? arr)
      (do
        (util/info " tool args: %s\n"
                (.toString builder))
        arry)
      (recur (rest arr)
             (-> builder
                 (.append " ")
                 (.append (first arr)))))));

(defn run-antlr4!
  [args show]
  (when show (show-array args))
  (util/info "running antlr4: %s\n"
    (with-err-str
        (let [antlr (Tool. (into-array args))]
          (.processGrammarsOnCommandLine antlr)))))

;; https://github.com/antlr/antlr4/blob/master/doc/tool-options.md
;; https://github.com/antlr/antlr4/blob/master/tool/src/org/antlr/v4/Tool.java
(deftask antlr4
  "A task that generates parsers and lexers from antlr grammars."
  [g grammar        FILE    str    "grammar file"
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
  (if-not grammar
    (do
      (boot.util/fail "The --grammar argument is required")
      (*usage*)))

  (let [target-dir (boot/tmp-dir!)
        target-dir-str (.getCanonicalPath target-dir)]
    (fn middleware [next-handler]
      (fn handler [fileset]

        ;; outward/pre processing
        ;; the main goal here is to prduce
        ;; the fileset for the next-handler

        (boot/empty-dir! target-dir)
        (util/info "target: %s\n" target-dir-str)
        ;; load the tokens files into the tmp-dir
        (let [in-files (boot/input-files fileset)
              token-files (boot/by-ext [".tokens"] in-files)]
          (doseq [tf token-files]
            (let [rel-path  (boot/tmp-path tf)
                  tgt-file  (io/file target-dir rel-path)
                  src-file  (boot/tmp-file tf)]
              (util/info "token: %s %s\n" src-file tgt-file)
              (io/copy src-file tgt-file))))

        (let [grammar-file (boot/tmp-get fileset grammar)
              grammar-file' (boot/tmp-file grammar-file)
              grammar-file-str (.getCanonicalPath grammar-file')]
          ;(util/info "grammar: %s\n" grammar)
          ;(util/info "source: %s\n" source)
          ;(util/info "target: %s\n" target)
          (util/info "compiling grammar: %s\n" grammar)

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
              (conj! grammar-file-str)
              persistent!
              (run-antlr4! show))

          ;; prepare fileset and call next-handler
          (let [fileset' (-> fileset
                             (boot/add-resource target-dir)
                             boot/commit!)
                result (next-handler fileset')]
            ;; inbound/post processing
            ;; the goal here is to perform any side effects
            result))))))

;;(deftesttask antlr4-tests []
;;  (comp (antlr4 :grammar "AqlCommentTest.g4" :show 5)
;;        (to-asset-invert-tests))))

(deftask foo
  "A task that generates parsers and lexers from antlr grammars."
  [g grammar        FILE    str    "grammar file"]
  (let [target-dir (boot/tmp-dir!)
        target-dir-str (.getCanonicalPath target-dir)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        ;(boot/empty-dir! target-dir)
        (util/info "target: %s\n" target-dir-str)
        ;; pre-processing
        ;; produce the fileset for the next-handler                              ; [7]
        (let [fileset'  (... fileset)
              fileset'' (boot/commit! fileset')
              result    (next-handler fileset'')]
          ;; post-processing
          ;; produce any side effects
          result)))))
