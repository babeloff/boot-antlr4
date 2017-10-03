# antlr4

Boot tasks to work with antlr4.

## Usage

Anltr4 is a parser and lexer generator.

Run the `antlr4-pre` task:

    $ boot antlr4-pre

To use this in your project, add `[babeloff/antlr4 "0.1.0-SNAPSHOT"]` to your `:dependencies`
and then require the task:

    (require '[babeloff.antlr4 :as antlr :refer [antlr4]])

Other future tasks include: `antlr4-interpret`.

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

## To Do

The output from the parser can be used in serveral ways.

- [x] construct a task for generating lexers and parsers from grammars
- [ ] compiled into a package for external consuptions
- [ ] compiled into the current POD so it can be used to parse subsequent input files


## License

Copyright © 2017 Fredrick P. Eisele

Distributed under the Eclipse Public License either
version 1.0 or (at your option) any later version.
