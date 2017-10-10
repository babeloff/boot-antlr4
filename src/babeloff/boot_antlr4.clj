(ns babeloff.boot-antlr4
  "an antlr task that builds lexers and parsers."
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
                                 Lexer Parser 
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
      (-> args
          (conj! option)
          (conj! object)))
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
  (-> args
    (conj! "-lib")
    (conj! (or source
             (.getCanonicalPath (first (boot/input-dirs fileset)))))))

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

;; https://github.com/antlr/antlr4/blob/master/tool/src/org/antlr/v4/Tool.java
(defn run-antlr4!
  ""
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


;; https://github.com/antlr/antlr4/blob/master/tool/src/org/antlr/v4/gui/TestRig.java
;; this does some fancy stuff ...
;; dyanmically import classes and runs their constructors.
(deftask test-rig
  "A task that runs parsers and lexers against samples."
  [p parser         CLASS   str    "parser name"
   l lexer          CLASS   str    "lexer name"
   r start-rule     LIB_DIR str    "the name of the first rule to match"
   z postscript     CODE    str    "produce a postscript output of the parse tree"
   e encoding       STYLE   str    "specify output STYLE for messages in antlr, gnu, vs2005"
   i input          OPT     [str]  "the file name of the input to parse"
   s show                   bool   "show the constructed properties"
   a tokens                 bool   "produce the annotated token stream"
   t tree                   bool   "produce the annotated parse tree"
   v gui                    bool   "produce the parse tree as a window"
   x trace                  bool   "show the progress that the parser makes"
   d diagnostics            bool   "show some diagnostics"
   f sll                    bool   "use the fast SLL prediction mode"]
  (if-not parser
    (do
      (boot.util/fail "The --parser argument is required")
      (*usage*)))
  (if-not start-rule
    (do
      (boot.util/fail "The --start-rule argument is required")
      (*usage*)))

  (let [target-dir (boot/tmp-dir!)
        target-dir-str (.getCanonicalPath target-dir)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (boot/empty-dir! target-dir)
        (util/info "target: %s\n" target-dir-str)
        (util/info "parser: %s\n" parser)
        (util/info "lexer: %s\n" lexer)

       (when show
         (util/info "parse options: %s\n" *opts*))

        (let [class-loader (DynamicClassLoader.)
              lexer-class ^Lexer (.loadClass class-loader lexer) 
              lexer-inst (Reflector/invokeConstructor lexer-class 
                            (into-array CharStream [nil]))

              parser-class ^Parser (.loadClass class-loader parser)
              parser-inst (Reflector/invokeConstructor parser-class 
                            (into-array  TokenStream [nil]))

             char-set (Charset/defaultCharset)]

          (doseq [file input]
            (util/info "input: %s & %s\n" 
              (-> (Paths/get "." (make-array String 0)) 
                  .toAbsolutePath .normalize .toString)
              file)
          
            (let [char-stream (-> (Paths/get file (make-array String 0))
                                 (CharStreams/fromPath char-set))
                  _ (.setInputStream lexer-inst char-stream)
                  token-stream (CommonTokenStream. lexer-inst)]
              
              (.fill token-stream)

              (when tokens
                (util/info "tokens enabled \n")
                (let [out-path (str file ".tokens") 
                      has-dirs? (io/make-parents out-path)] 
                  (with-open [wtr (io/writer out-path 
                                      :encoding "UTF-8"
                                      :append true)]
                    (doseq [tok (.getTokens token-stream)]
                      (.write wtr (str (if (instance? CommonToken tok)
                                        (.toString tok lexer-inst)
                                        (.toString tok))
                                      "\n"))))))

              (when diagnostics
                (util/info "enable diagnostics \n")
                (.addErrorListener parser-inst (DiagnosticErrorListener.))
                (-> parser-inst
                    .getInterpreter
                   (.setPredictionMode PredictionMode/LL_EXACT_AMBIG_DETECTION)))

              (when (or tree gui postscript)
                (util/info "enable parse tree \n")
                (.setBuildParseTree parser-inst true))

               (when sll
                 (util/info "use SLL \n")
                (-> parser-inst
                     .getInterpreter
                     (.setPredictionMode PredictionMode/SLL)))

              (doto parser-inst
                (.setTokenStream token-stream))
                ;;(.setTrace trace))

              ;; https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Reflector.java#L319
               (try
                (let [parse-tree (Reflector/invokeInstanceMember 
                                    parser-inst start-rule)]
                   (when tree
                    (util/info "print tree \n" (.toStringTree parse-tree parser-inst)))
                  (when gui
                     (util/info "inspect tree \n")
                    (Trees/inspect parse-tree parser-inst))
                   (when postscript
                     (util/info "print tree as postscript \n")
                    (Trees/save parse-tree parser-inst postscript)))
                 (catch NoSuchMethodException ex
                        (util/info "no method for rule %s or it has arguements \n"
                                   start-rule))
                 (finally
                         (util/info "releasing"))))))

        ;; prepare fileset and call next-handler
       (let [fileset' (-> fileset
                          (boot/add-resource target-dir)
                          boot/commit!)
             result (next-handler fileset')]
        ;; post processing
        ;; the goal here is to perform any side effects
        result)))))
