# demo project : literate antlr4


This demo does two things:

1. Demonstrates how the provided tasks may be used to do live-coding
2. Extend the Antlr4 grammar itself to allow a literate programming style.

## To Do

- [ ] 

## Live-coding

You can get a live-coding behavior with a build.boot like the following:

    (deftask build
      [s show bool "show the arguments"]
      (comp
        (watch)                                           ;; [1]
        (antlr4 :grammar "AqlLexerRules.g4" :show show)   ;; [2]
        (antlr4 :grammar "Aql.g4" :show show)             ;; [3]
        (target :dir #{"target"})))                       ;; [4]
        
This sample does not use the interpreter as the lexer makes use of constructs
which are not compatable with a combined grammar.
The [1] enables the live-coding experience with the source files being watched.
The [2] lexer is constructed first and its output including the lexer tokens 
are included in the fileset passed to the next phase.
The [3] generated files are placed in the target directory.

## Literate Antlr4

This is a work in progress.

