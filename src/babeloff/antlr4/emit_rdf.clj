(ns babeloff.antlr4.emit-rdf
  "an antlr task that builds lexers and parsers."
  (:require
    (boot [util :as util])
    (babeloff [uuid :as uuid])
    (clojure.spec [alpha :as s])
    (clojure.spec.test [alpha :as stest])
    [rdf :as rdf])
  (:import
    (org.antlr.v4.runtime.atn PredictionMode
                              ATN)))

(defn node->get-name
  [this rule-list]
  (let [rule-index (.. this (getRuleContext) (getRuleIndex))
        rule-name (.get rule-list rule-index)
        alt-number (.. this (getAltNumber))]
    (keyword (if (= alt-number ATN/INVALID_ALT_NUMBER)
               rule-name
               (concat rule-name ':' alt-number)))))

(defn node->literal
  [this]
  (let [payload (.. this (getPayload))]
    (if (instance? org.antlr.v4.runtime.Token payload)
      (.. payload (getText))
      (.. payload (toString)))))

(s/def ::graph some?)

(defn node->rdf-this-triples
  "add triples that every node must have"
  [graph this this-rdf-node parent uuid-dict rule-list]
  {:post [(s/assert ::graph %)]}
  (cond
   (instance? org.antlr.v4.runtime.RuleContext this)
   (rdf/add-triple graph
     (rdf/triple
        this-rdf-node
        (rdf/iri "http://immortals.brass.darpa.gov/type")
        (rdf/literal (str (node->get-name this rule-list)))))

   (instance? org.antlr.v4.runtime.tree.ErrorNode this)
   (rdf/add-triple graph
     (rdf/triple
      this-rdf-node
      (rdf/iri "http://immortals.brass.darpa.gov/error")
      (rdf/literal (.toString this))))

   :else graph))


(defn node->rdf-parent-triples
  "add triples that refer to the parent of the node"
  [graph this this-rdf-node parent uuid-dict rule-list lost-node]
  {:post [(s/assert ::graph %)]}
  (if (nil? parent)
    graph
    (let [parent-rdf-node (get uuid-dict parent lost-node)
          parent-name (node->get-name parent rule-list)]
      (cond
       (instance? org.antlr.v4.runtime.RuleContext this)
       (rdf/add-triple graph
           parent-rdf-node
           (rdf/iri "http://immortals.brass.darpa.gov/context")
           this-rdf-node)

       (instance? org.antlr.v4.runtime.tree.TerminalNode this)
       (let [symbol (.. this (getSymbol))]
         (-> graph
           (rdf/add-triple
             parent-rdf-node
             (rdf/iri "http://immortals.brass.darpa.gov/terminal")
             this-rdf-node)
           (rdf/add-triple
             this-rdf-node
             (rdf/iri "http://immortals.brass.darpa.gov/literal")
             (rdf/literal (if (nil? symbol)
                            (.. symbol (getText))
                            (node->literal this))))))
       :else
       (rdf/add-triple graph
           parent-rdf-node
           (rdf/iri "http://immortals.brass.darpa.gov/other")
           (rdf/literal (node->literal this)))))))

(defn node->rdf-order-triples
  "Produce triples that connect the root node to all
  of the terminal nodes in the order they appear in the
  parse tree.
  This exactly persists the order from the original file."
  [graph this this-rdf-node that-rdf-node]
  {:post [(s/assert ::graph %)]}
  (cond
    (instance? org.antlr.v4.runtime.tree.TerminalNode this)
    (rdf/add-triple graph
      (rdf/triple
         that-rdf-node
         (rdf/iri "http://immortals.brass.darpa.gov/next")
         this-rdf-node))

    :else graph))

(defn node->lost-triples
  "A node to attach lost things to."
  [graph this-rdf-node]
  {:post [(s/assert ::graph %)]}
  (rdf/add-triple graph
    (rdf/triple
      this-rdf-node
      (rdf/iri "http://immortals.brass.darpa.gov/type")
      (rdf/literal "LOST-NODE"))))

(s/def ::context (s/keys :req [::uuid-dict ::graph ::last-node]))

(defn node->rdf-seq
  "A naive export of the node to RDF triples.
  The node is considered the this of the triple."
  [context this rule-list lost-node]
  {:post [(s/assert ::graph %)]}
  (let [{uuid-dict :uuid-dict
         graph :graph
         last-node :last-node} context
        this-rdf-node (rdf/blanknode)
        uuid-dict (assoc uuid-dict this this-rdf-node)
        parent (.getParent this)
        new-graph
          (-> graph
            (node->rdf-parent-triples this this-rdf-node
              parent uuid-dict rule-list lost-node)
            (node->rdf-this-triples this this-rdf-node
              parent uuid-dict rule-list)
            (node->rdf-order-triples this this-rdf-node last-node))]

      {:uuid-dict uuid-dict
       :graph new-graph
       :last-node
       (cond
         (nil? last-node) this-rdf-node
         (instance? org.antlr.v4.runtime.tree.TerminalNode this) this-rdf-node
         :else last-node)}))

(s/def ::triple-vector (s/coll-of (s/coll-of any? :kind vector?) :kind vector?))
(s/fdef node->rdf-seq :ret ::triple-vector)
; (stest/instrument `node->rdf-seq)

(s/def ::engine #{:clojure :simple :jena})

(defn tree->rdf-graph
  "A naive export of the parse tree to RDF triples.
  This needs to be lossless as we will want the process
  to be easily reversable.
  The idea here is that manipulating a parse tree in a
  graph database makes a lot of sense."
  [root engine rule-list]
  {:pre [(s/assert ::engine engine)]}
  (rdf/with-rdf engine
    (let [lost-node (rdf/blanknode)
          graph (rdf/graph)]
      (node->lost-triples graph lost-node)
      (get
        (reduce
          #(node->rdf-seq %1 %2 rule-list lost-node)
          {:uuid-dict {} :graph graph :last-node nil}
          (tree-seq
            #(instance? org.antlr.v4.runtime.RuleContext %)
            #(for [ix (range 0 (.getChildCount %) 1)]
                (.getChild % ix))
            root))
        :graph))))
