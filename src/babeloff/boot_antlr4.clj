(ns babeloff.boot-antlr4
  "an antlr task that builds lexers and parsers."
  {:boot/export-tasks true}
  (:require (boot [core :as boot :refer [deftask]]
                  [util :as util]
                  [task-helpers :as helper])
            (clojure [pprint :as pp]
                     [edn :as edn]
                     [reflect :as reflect]
                     [string :as string])
            (clojure.java [io :as io]
                          [classpath :as cp])
            (clojure.spec [alpha :as s])
            (me.raynes [fs :as fs]))
  (:import (clojure.lang DynamicClassLoader
                         Reflector)
           (clojure.asm ClassReader)
           (org.antlr.v4 Tool)
           (org.antlr.v4.tool LexerGrammar
                              Grammar)
           (org.antlr.v4.parse ANTLRParser)
           (org.antlr.v4.runtime RuleContext
                                 CharStream CharStreams
                                 CommonToken CommonTokenStream
                                 DiagnosticErrorListener
                                 Lexer Parser
                                 Token TokenStream)
           (org.antlr.v4.runtime.atn PredictionMode
                                     ATN)
           (org.antlr.v4.runtime.tree.Trees)
           (org.antlr.v4.runtime.tree ParseTree)
           (java.nio.file Paths)
           (java.nio.charset Charset)
           (java.io IOException)
           (org.antlr.v4.gui.Trees)
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
                 (.append (first arr)))))))


;;  Imitate the behavior of
;;    https://github.com/antlr/antlr4/blob/master/runtime/Java/src/org/antlr/v4/runtime/RuleContext.java
;;    https://github.com/antlr/antlr4/blob/master/runtime/Java/src/org/antlr/v4/runtime/tree/Trees.java
;;
;;   recursively build an array tree of nodes and their values.

;; https://github.com/antlr/antlr4/blob/master/runtime/Java/src/org/antlr/v4/runtime/tree/Trees.java#L70
(defn node->string
  "A naive export of the node to RDF triples."
  [node rule-list]
  ;(try)
  (cond
    (instance? org.antlr.v4.runtime.RuleContext node)
    (let [rule-index (.. node (getRuleContext) (getRuleIndex))
          rule-name (.get rule-list rule-index)
          alt-number (.. node (getAltNumber))]
      (if (= alt-number ATN/INVALID_ALT_NUMBER)
        rule-name
        (concat rule-name ':' alt-number)))

    (instance? org.antlr.v4.runtime.tree.ErrorNode node)
    (.. node (toString))

    (instance? org.antlr.v4.runtime.tree.TerminalNode node)
    (let [symbol (.. node (getSymbol))]
      (if (nil? symbol)
        (.. symbol (getText))
        (let [payload (.. node (getPayload))]
          (if (instance? org.antlr.v4.runtime.Token payload)
            (.. payload (getText))
            (.. payload (toString))))))

    :else
    (let [payload (.. node (getPayload))]
      (if (instance? org.antlr.v4.runtime.Token payload)
        (.. payload (getText))
        (.. payload (toString))))))

    ;(catch java.lang.IllegalArgumentException ex
    ;  (if  (and (s/valid? ::node node))
    ;    (util/warn "exception: %s \n"
    ;      (with-err-str (clojure.stacktrace/print-stack-trace ex)))
    ;    (util/warn "exception: %s \n"
    ;      (with-err-str (clojure.stacktrace/print-stack-trace ex)))))
    ;(finally)))


(s/def ::node (s/and inst?))
(s/def ::rule-s (s/and inst? #(instance? java.util.List %)))
(s/fdef node->string
  :args (s/cat :node ::node :rule-s ::rule-s)
  :ret string?)

(defn tree->edn-tree
  "A straight up reimplementation of ...
  https://github.com/antlr/antlr4/blob/master/runtime/Java/src/org/antlr/v4/runtime/tree/Trees.java#L48
  public static String toStringTree(final Tree t, final List<String> ruleNames) {}"
  [node rule-list]
  (let [s (node->string node rule-list)
        count (.getChildCount node)]
    (if (> 1 count)
      s
      (let [sb (transient [(keyword s)])]
        (doseq [ix (range 0 count 1)]
          (conj! sb (tree->edn-tree (.getChild node ix) rule-list)))
        (persistent! sb)))))

(defn node->rdf-seq
  "A naive export of the node to RDF triples."
  [node rule-s]
  (cond
    (instance? org.antlr.v4.runtime.RuleContext node)
    (let [rule-index (.. node (getRuleContext) (getRuleIndex))
          rule-name (.get rule-s rule-index)
          alt-number (.. node (getAltNumber))]
      (if (= alt-number ATN/INVALID_ALT_NUMBER)
        rule-name
        (concat rule-name ':' alt-number)))

    (instance? org.antlr.v4.runtime.tree.ErrorNode node)
    (.. node (toString))

    (instance? org.antlr.v4.runtime.tree.TerminalNode node)
    (let [symbol (.. node (getSymbol))]
      (if (nil? symbol)
        (.. symbol (getText))
        (let [payload (.. node (getPayload))]
          (if (instance? org.antlr.v4.runtime.Token payload)
            (.. payload (getText))
            (.. payload (toString))))))

    :else
    (let [payload (.. node (getPayload))]
      (if (instance? org.antlr.v4.runtime.Token payload)
        (.. payload (getText))
        (.. payload (toString))))))


(defn tree->rdf-seq
  "A naive export of the parse tree to RDF triples.
  This needs to be lossless as we will want the process
  to be easily reversable.
  The idea here is that manipulating a parse tree in a
  graph database makes a lot of sense."
  [root rule-list]
  (persistent!
    (reduce
      (fn [rdf-seq node]
          (reduce #(conj! %1 %2)
                   rdf-seq
                   (node->rdf-seq node rule-list)))
      (transient [])
      (tree-seq
        #(instance? org.antlr.v4.runtime.tree.TerminalNode %)
        #(for [ix (range 0 (.getCount %) 1)]
            (.getChild % ix))
        root))))


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
(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (when xin
      (io/copy xin xout)
      (.toByteArray xout))))

(defn define-class
 [class-loader class-name class-path]
 ;; (if (.findInMemoryClass class-loader class-name)
 (try
    (let [class-bytes (file->bytes class-path)]
     (when class-bytes
       (.defineClass class-loader class-name class-bytes "")))
    (catch java.lang.LinkageError ex
      (util/info "already loaded: %s \n" class-name)
      "")))

(defn class-path->name
  [path]
  (let [package (drop-last (fs/split path))
        name (fs/base-name path ".class")]
    ;; (util/info "path name: %s %s %s\n" path (doall package) name)
    (string/join "." (concat package [name]))))

(defn define-class-family
  "dynamically load the class and its internal classes
   using the provided class loader.
   In its current form the class-name is ignored."
  [fileset class-loader class-name]
  (let [in-file-s (boot/input-files fileset)
        class-file-s (boot/by-ext [".class"] in-file-s)]
    (doseq [in class-file-s]
      (let [class-file (boot/tmp-file in)
            class-path (boot/tmp-path in)
            class-name (class-path->name class-path)]
        ; (util/info "load class: %s & %s\n" class-name class-path)
        (define-class class-loader class-name class-file)))))

(defn get-target-path
  [file-path]
  (->> (fs/split file-path)
       (map #(case % ".." "dots" "." "dot" %))))

;; https://github.com/antlr/antlr4/blob/master/tool/src/org/antlr/v4/gui/TestRig.java
;; this does some fancy stuff ...
;; dyanmically import classes and runs their constructors.
;; the gui option from TestRig is not supported.
(deftask test-rig
  "A task that runs parsers and lexers against samples."
  [p parser         CLASS   str    "parser name"
   l lexer          CLASS   str    "lexer name"
   r start-rule     LIB_DIR str    "the name of the first rule to match"
   e encoding       STYLE   str    "specify output STYLE for messages in antlr, gnu, vs2005"
   i input          OPT     [str]  "the file name of the input to parse"
   s show                   bool   "show the constructed properties"
   a tokens                 bool   "produce the annotated token stream"
   t tree                   bool   "produce the annotated parse tree in lisp form"
   _ edn                    bool   "produce the annotated parse tree in edn form"
   _ rdf                    bool   "produce the annotated parse tree in rdf form"
   z postscript             bool   "produce a postscript output of the parse tree"
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
        (util/info "working directory: %s\n"
          (-> (Paths/get "." (make-array String 0))
             .toAbsolutePath .normalize .toString))

       (when show
         (util/info "parse options: %s\n" *opts*))

       (let [class-loader (DynamicClassLoader.)
             _ (define-class-family fileset class-loader parser)

             lexer-class ^Lexer (.loadClass class-loader lexer)
             lexer-inst (Reflector/invokeConstructor lexer-class
                                 (into-array CharStream [nil]))

             parser-class ^Parser (.loadClass class-loader parser)
             parser-inst (Reflector/invokeConstructor parser-class
                           (into-array  TokenStream [nil]))

             char-set (Charset/defaultCharset)]

         (doseq [file-path input]
           (util/info "input: %s\n" file-path)

           (let [tgt-file-path (apply io/file target-dir (get-target-path file-path))
                 src-file (Paths/get file-path (make-array String 0))
                 char-stream (CharStreams/fromPath src-file char-set)
                 _ (.setInputStream lexer-inst char-stream)
                 token-stream (CommonTokenStream. lexer-inst)]

             (util/info "preparing token stream\n" file-path)
             (.fill token-stream)

             (util/info "token stream filled\n" file-path)
             (when tokens
               (util/info "tokens enabled \n")
               (let [out-path (io/file tgt-file-path "token.txt")
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

             (when (or tree postscript edn rdf)
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
                                  parser-inst start-rule)
                    rule-array (when parser-inst (.getRuleNames parser-inst))
                    rule-list (when rule-array (java.util.Arrays/asList rule-array))]
                (when tree
                  (util/info "write parse tree as lisp\n")
                  (let [out-path (io/file tgt-file-path "tree.lisp")
                        has-dirs? (io/make-parents out-path)
                        lisp-tree-str (.toStringTree parse-tree rule-list)]
                    (with-open [wtr (io/writer out-path
                                        :encoding "UTF-8"
                                        :append true)]
                        ;; (pp/pprint rule-list wtr)
                        (.write wtr lisp-tree-str))))

                (when edn
                  (util/info "write parse tree as EDN\n")
                  (let [out-path (io/file tgt-file-path "tree.edn")
                        has-dirs? (io/make-parents out-path)
                        edn-tree (tree->edn-tree parse-tree rule-list)]
                    (with-open [wtr (io/writer out-path
                                        :encoding "UTF-8"
                                        :append true)]
                        ;; (pp/pprint rule-list wtr)
                        (pp/pprint edn-tree wtr))))


                (when rdf
                  (util/info "write parse tree as RDF\n")
                  (let [out-path (io/file tgt-file-path "tree.ttl")
                        has-dirs? (io/make-parents out-path)
                        rdf-seq (tree->rdf-seq parse-tree rule-list)]
                    (with-open [wtr (io/writer out-path
                                        :encoding "UTF-8"
                                        :append true)]
                        ;; (pp/pprint rule-list wtr)
                        (pp/pprint rdf-seq wtr))))

                (when postscript
                  (util/info "write parse tree as postscript\n")
                  (let [out-path (io/file tgt-file-path "tree.ps")
                        has-dirs? (io/make-parents out-path)]
                    (org.antlr.v4.gui.Trees/save
                          parse-tree parser-inst (.getAbsolutePath out-path)))))

              (catch NoSuchMethodException ex
                    (util/info "no method for rule %s or it has arguements \n"
                                start-rule))
              (finally
                      (util/info "releasing %s\n" file-path))))))

          ;; prepare fileset and call next-handler
       (let [fileset' (-> fileset
                           (boot/add-asset target-dir)
                           boot/commit!)
             result (next-handler fileset')]
         ;; post processing
         ;; the goal here is to perform any side effects
         result)))))
