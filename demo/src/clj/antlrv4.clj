(ns ANTLRv4
  (:import (org.antlr.parser.antlr4 ANTLRv4vLexer
                                    ANTLRv4Parser)))

(defn construct []
  {::lexer (ANTLRv4vLexer. nil)
   ::parser (ANTLRv4Parser. nil)})
