(ns babeloff.antlr4.emit-edn
  "an antlr task that builds lexers and parsers."
  (:require
    (clojure.spec [alpha :as s])
    (clojure.spec.test [alpha :as stest]))
  (:import
    (org.antlr.v4.runtime.atn PredictionMode
                              ATN)))

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
(s/def ::rule-list (s/and inst? #(instance? java.util.List %)))
(s/fdef node->string
  :args (s/cat :node ::node :rule-list ::rule-list)
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
