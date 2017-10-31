# boot-antlr4

[![Build Status](https://travis-ci.org/babeloff/rdf-clj.svg?branch=master)](https://travis-ci.org/babeloff/boot-antlr4)
[![Clojars Project](https://img.shields.io/clojars/v/babeloff/boot-antlr4.svg)](https://clojars.org/babeloff/boot-antlr4)

Boot tasks to work with antlr4.
This is alpha software; it is under ongoing development.
Backward compatibility is **not** guaranteed.

See https://clojars.org/babeloff/boot-antlr4

## Usage

Anltr4 is a parser and lexer generator.

Run the `antlr4` task:

    $ boot antlr4 -g AqlLexerRules.g4

Then require the task:

    (require '[babeloff.antlr4 :as antlr :refer [generate exercise]])

The `generate` task generates java classes from the antlr4 grammar files.
The `exercise` task provides some basic manipulations using the antlr4 TestRig.
For usage please study the
[parser project](https://github.com/babeloff/boot-antlr4-parser/README.md)
which demonstrates how to extend the Antlr4 grammar itself
to allow a literate programming style.

You can get started with live-coding with a build.boot like the following:

    (deftask build
      [s show bool "show the arguments"]
      (comp
        (watch)                                   ;; [1]
        (generate :grammar "AqlLexerRules.g4")    ;; [2]
        (generate :grammar "Aql.g4")              ;; [3]
        (target :dir #{"target"})))               ;; [4]

The [1] enables the live-coding experience with the source files being watched.
The [2] lexer is constructed first and its output including the lexer tokens
are included in the fileset passed to the next phase.
The [3] generated files are placed in the target directory.


## License

Copyright © 2017 Fredrick P. Eisele

Distributed under the Eclipse Public License either
version 1.0 or (at your option) any later version.

## Notes

This approach is an alternative to other development
environments which use an interpreter.
The interpreter has some limitations as currently written.
In particular as the lexer makes use of constructs
which are not compatible with a combined grammar.
