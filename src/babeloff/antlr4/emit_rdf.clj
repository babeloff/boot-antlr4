(ns babeloff.antlr4.emit-rdf
  "an antlr task that builds lexers and parsers."
  (:require
    (clojure.spec [alpha :as s])
    (clojure.spec.test [alpha :as stest]))
  (:import
    (org.antlr.v4.runtime.atn PredictionMode
                              ATN)))

(defn node->rdf-subject
  [node rule-list]
  (let [rule-index (.. node (getRuleContext) (getRuleIndex))
        rule-name (.get rule-list rule-index)
        alt-number (.. node (getAltNumber))]
    (keyword (if (= alt-number ATN/INVALID_ALT_NUMBER)
               rule-name
               (concat rule-name ':' alt-number)))))

(defn node->literal
  [node]
  (let [payload (.. node (getPayload))]
    (if (instance? org.antlr.v4.runtime.Token payload)
      (.. payload (getText))
      (.. payload (toString)))))

(defn node->rdf-seq
  "A naive export of the node to RDF triples.
  The node is considered the object of the triple."
  [context node rule-list]
  (let [uuid-dict @(:uuid context)
        subject (.getParent node)]
    (if (nil? subject)
      [[:root]]
      (let [subject-uuid (get uuid-dict subject :not-found)
            ;subject-url ((:make-url context) subject subject-uuid)
            subject-name (node->rdf-subject subject rule-list)
            object-uuid (get uuid-dict node :not-found)]
       (cond
        (instance? org.antlr.v4.runtime.RuleContext node)
        (let [object (node->rdf-subject node rule-list)]
          [[subject-uuid "context" object-uuid]
           [object-uuid "type" object]])

        (instance? org.antlr.v4.runtime.tree.ErrorNode node)
        [[subject-uuid "error" (.. node (toString))]]

        (instance? org.antlr.v4.runtime.tree.TerminalNode node)
        [(let [symbol (.. node (getSymbol))]
          (if (nil? symbol)
            [subject-uuid "literal" (.. symbol (getText))]
            [subject-uuid "literal" (node->literal node)]))]

        :else
        [[subject "literal" (node->literal node)]])))))

(s/def ::triple-vector (s/coll-of (s/coll-of any? :kind vector?) :kind vector?))
(s/fdef node->rdf-seq :ret ::triple-vector)
(stest/instrument `node->rdf-seq)

(defn tree->rdf-seq
  "A naive export of the parse tree to RDF triples.
  This needs to be lossless as we will want the process
  to be easily reversable.
  The idea here is that manipulating a parse tree in a
  graph database makes a lot of sense."
  [root rule-list]
  (let [context {:uuid (atom {})}]
    (persistent!
      (reduce
        (fn [acc0 node]
           (swap! (:uuid context) assoc node (java.util.UUID/randomUUID))
           (reduce
             (fn [acc1 triple] (conj! acc1 triple))
             acc0
             (node->rdf-seq context node rule-list)))
        (transient [])
        (tree-seq
          #(instance? org.antlr.v4.runtime.RuleContext %)
          #(for [ix (range 0 (.getChildCount %) 1)]
              (.getChild % ix))
          root)))))
