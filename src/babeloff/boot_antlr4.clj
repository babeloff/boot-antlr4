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
            (clojure.spec.test [alpha :as stest])
            (me.raynes [fs :as fs])
            [rdf :as rdf]
            (babeloff [options :as opt]
                      [dynamic-import :as importer])
            (babeloff.antlr4 [emit-edn :as emit-edn]
                             [emit-rdf :as emit-rdf]
                             [util :as antlr]
                             [interpret :as antlr-interpret]))
  (:import (org.antlr.v4.tool Grammar)
           (org.antlr.v4.runtime RuleContext
                                 CharStream CharStreams
                                 CommonToken CommonTokenStream
                                 Lexer Parser
                                 Token TokenStream)
           (org.antlr.v4.runtime.atn ATN)
           (java.nio.file Paths)
           (java.nio.charset Charset)
           (org.antlr.v4.gui Trees)
           (java.lang.reflect Constructor
                              Method)
           (java.util List)))



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

          (let [args (-> (transient ["-o" target-dir-str])
                         (opt/source! source fileset)

                         (opt/value! "-encoding" encoding)
                         (opt/value! "-message-format" message-format)
                         (opt/value! "-package" package)

                         (opt/override! override)

                         (opt/bool! "-atn" atn)
                         (opt/bool! "-long-messages" long-messages)
                         (opt/bool! ["-listener" "-no-listener"] listener)
                         (opt/bool! ["-visitor" "-no-visitor"] visitor)
                         (opt/bool! "-depend" depend)

                         (opt/bool! "-Werror" warn-error)
                         (opt/bool! "-XdbgST" debug-st)
                         (opt/bool! "-Xsave-lexer" save-lexer)
                         (opt/bool! "-XdbgSTWait" debug-st-wait)
                         (opt/bool! "-Xforce-atn" force-atn)
                         (opt/bool! "-Xlog" log)
                         (conj! grammar-file-str)
                         persistent!)]
            (util/info "running antlr4: %s\n" args)
            ;(if show (show-array args))
            (opt/with-err-str (antlr/run-tool! args)))

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
        (try
          (importer/define-class class-loader class-name class-file)
          (catch java.lang.LinkageError ex
            (util/info "already loaded: %s \n" class-name)
            ""))))))


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

       (let [class-loader (importer/make-loader)
             _ (define-class-family fileset class-loader parser)

             lexer-class ^Lexer (importer/load-class class-loader lexer)
             lexer-inst (importer/invoke-constructor lexer-class
                                 (into-array CharStream [nil]))

             parser-class ^Parser (importer/load-class class-loader parser)
             parser-inst (importer/invoke-constructor parser-class
                           (into-array  TokenStream [nil]))

             char-set importer/default-charset]

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
               (antlr-interpret/dianostics parser-inst))

             (when (or tree postscript edn rdf)
               (util/info "enable parse tree \n")
               (.setBuildParseTree parser-inst true))

             (when sll
               (util/info "use SLL \n")
               (antlr-interpret/sll parser-inst))

             (doto parser-inst
               (.setTokenStream token-stream))
                ;;(.setTrace trace))

              ;; https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Reflector.java#L319
             (try
              (let [parse-tree (importer/invoke-inst-member
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
                        edn-tree (emit-edn/tree->edn-tree parse-tree rule-list)]
                    (with-open [wtr (io/writer out-path
                                        :encoding "UTF-8"
                                        :append true)]
                        ;; (pp/pprint rule-list wtr)
                        (pp/pprint edn-tree wtr))))


                (when rdf
                  (util/info "write parse tree as RDF\n")
                  (let [out-path (io/file tgt-file-path "tree.ttl")
                        has-dirs? (io/make-parents out-path)
                        rdf-graph (emit-rdf/tree->rdf-graph parse-tree rule-list)]
                    (with-open [wtr (io/writer out-path
                                        :encoding "UTF-8"
                                        :append true)]
                      (doseq [triple (.iterate rdf-graph)]
                        (.write wtr (.toString triple))
                        (.write wtr "\n")))))

                (when postscript
                  (util/info "write parse tree as postscript\n")
                  (let [out-path (io/file tgt-file-path "tree.ps")
                        has-dirs? (io/make-parents out-path)]
                    (Trees/save
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
