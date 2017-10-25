(ns babeloff.antlr4.emit-rdf
  "an antlr task that builds lexers and parsers."
  (:require
    (babeloff [uuid :as uuid])
    (clojure.spec [alpha :as s])
    (clojure.spec.test [alpha :as stest])
    [rdf :as rdf])
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
  [context graph node rule-list]
  (let [uuid-dict-atom (:uuid context)
        uuid-dict (swap! uuid-dict-atom assoc node (rdf/blanknode))
        subject (.getParent node)]

    (if (nil? subject)
      graph
      (let [subject-node (get uuid-dict subject (rdf/blanknode))
            subject-name (node->rdf-subject subject rule-list)
            object-node (get uuid-dict node (rdf/blanknode))]
          (cond
           (instance? org.antlr.v4.runtime.RuleContext node)
           (let [object (node->rdf-subject node rule-list)]
             (-> graph
                 (rdf/add-triple
                   (rdf/triple
                     subject-node
                     (rdf/iri "http://immortals.brass.darpa.gov/context")
                     object-node))
                 (rdf/add-triple
                   (rdf/triple
                      object-node
                      (rdf/iri "http://immortals.brass.darpa.gov/type")
                      (rdf/literal (str object))))))

           (instance? org.antlr.v4.runtime.tree.ErrorNode node)
           (rdf/add-triple graph
             (rdf/triple
                object-node
                (rdf/iri "http://immortals.brass.darpa.gov/error")
                (rdf/literal (.. node (toString)))))

           (instance? org.antlr.v4.runtime.tree.TerminalNode node)
           (let [symbol (.. node (getSymbol))]
             (rdf/add-triple graph
               (rdf/triple
                subject-node
                (rdf/iri "http://immortals.brass.darpa.gov/literal")
                (rdf/literal (if (nil? symbol)
                               (.. symbol (getText))
                               (node->literal node))))))

           :else
           (rdf/add-triple graph
             (rdf/triple
               subject-node
               (rdf/iri "http://immortals.brass.darpa.gov/literal")
               (rdf/literal (node->literal node)))))))))

(s/def ::triple-vector (s/coll-of (s/coll-of any? :kind vector?) :kind vector?))
(s/fdef node->rdf-seq :ret ::triple-vector)
; (stest/instrument `node->rdf-seq)

(defn tree->rdf-graph
  "A naive export of the parse tree to RDF triples.
  This needs to be lossless as we will want the process
  to be easily reversable.
  The idea here is that manipulating a parse tree in a
  graph database makes a lot of sense."
  [root rule-list]
  (let [context {:uuid (atom {})}]
    (rdf/with-rdf :simple
      (reduce
        #(node->rdf-seq context %1 %2 rule-list)
        (rdf/graph)
        (tree-seq
          #(instance? org.antlr.v4.runtime.RuleContext %)
          #(for [ix (range 0 (.getChildCount %) 1)]
              (.getChild % ix))
          root)))))
