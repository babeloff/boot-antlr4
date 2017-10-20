# boot-antlr4

Boot tasks to work with antlr4.

See https://clojars.org/babeloff/boot-antlr4

## Usage

Anltr4 is a parser and lexer generator.

Run the `antlr4` task:

    $ boot antlr4 -g AqlLexerRules.g4

To use this in your project, add `[babeloff/antlr4 "0.1.0-SNAPSHOT"]` to your `:dependencies`
and then require the task:

    (require '[babeloff.antlr4 :as antlr :refer [antlr4]])

For usage please study the [demo project](demo/README.md) which shows how
to extend the Antlr4 grammar itself to allow a literate programming style.

You can get started with live-coding with a build.boot like the following:

    (deftask build
      [s show bool "show the arguments"]
      (comp
        (watch)                                           ;; [1]
        (antlr4 :grammar "AqlLexerRules.g4" :show show)   ;; [2]
        (antlr4 :grammar "Aql.g4" :show show)             ;; [3]
        (target :dir #{"target"})))                       ;; [4]

The [1] enables the live-coding experience with the source files being watched.
The [2] lexer is constructed first and its output including the lexer tokens
are included in the fileset passed to the next phase.
The [3] generated files are placed in the target directory.

## To Do

The output from the parser can be used in serveral ways.

- [x] deftask [antlr4]: generating lexers and parsers from grammars
- [x] deftask [test-rig] : reimplement the Antlr4 TestRig
- [x] construct a demonstration project to show how to do live coding
- [ ] construct a package for external consuption
- [ ] functions for dynamically loading into the current POD


## License

Copyright © 2017 Fredrick P. Eisele

Distributed under the Eclipse Public License either
version 1.0 or (at your option) any later version.

## Notes

This approach is an alternative to other development
environments which use an interpreter.
The iterpreter has some limitations as currently written.
In particular as the lexer makes use of constructs
which are not compatable with a combined grammar.
